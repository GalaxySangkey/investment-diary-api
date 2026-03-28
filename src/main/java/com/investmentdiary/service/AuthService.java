package com.investmentdiary.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmentdiary.dto.auth.LoginRequest;
import com.investmentdiary.dto.auth.LoginResponse;
import com.investmentdiary.dto.auth.RegisterRequest;
import com.investmentdiary.dto.auth.UserInfo;
import com.investmentdiary.entity.User;
import com.investmentdiary.entity.UserSession;
import com.investmentdiary.event.UserRegisteredEvent;
import com.investmentdiary.exception.CustomAuthenticationException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.repository.UserRepository;
import com.investmentdiary.repository.UserSessionRepository;
import com.investmentdiary.security.JwtTokenProvider;
import com.investmentdiary.util.EncryptionUtil;

import jakarta.servlet.http.HttpServletRequest;

@Service
@Transactional(readOnly = true)
public class AuthService {
    
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EncryptionUtil encryptionUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordPolicyService passwordPolicyService;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public AuthService(UserRepository userRepository, 
                      UserSessionRepository userSessionRepository,
                      PasswordEncoder passwordEncoder,
                      JwtTokenProvider jwtTokenProvider,
                      EncryptionUtil encryptionUtil,
                      ApplicationEventPublisher eventPublisher,
                      PasswordPolicyService passwordPolicyService) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.encryptionUtil = encryptionUtil;
        this.eventPublisher = eventPublisher;
        this.passwordPolicyService = passwordPolicyService;
    }
    
    /**
     * 사용자 회원가입
     */
    @Transactional
    public UserInfo register(RegisterRequest request, HttpServletRequest httpRequest) {
        log.info("사용자 회원가입 시작: {}", request.getUsername());
        
        // 중복 사용자계정 확인
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomAuthenticationException("이미 사용 중인 사용자계정입니다.");
        }
        
        // 중복 이메일 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomAuthenticationException("이미 사용 중인 이메일입니다.");
        }
        
        // 전화번호 암호화
        String encryptedPhone = null;
        if (request.getPhone() != null) {
            encryptedPhone = encryptionUtil.encrypt(request.getPhone());
        }
        
        // 사용자 생성 (API는 닉네임만 받으므로, 엔티티의 필수 name은 닉네임으로 채움 — DB에는 암호화 저장)
        User user = User.builder()
            .username(request.getUsername())
            .password(passwordEncoder.encode(request.getPassword()))
            .email(request.getEmail())
            .name(request.getNickname().trim())
            .nickname(request.getNickname())
            .phoneEncrypted(encryptedPhone)
            .role(User.UserRole.USER)
            .status(User.UserStatus.ACTIVE)
            .build();
        
        User savedUser = userRepository.save(user);
        passwordPolicyService.markNewPasswordAccount(savedUser);
        
        eventPublisher.publishEvent(new UserRegisteredEvent(savedUser.getId()));
        
        log.info("사용자 회원가입 완료: {}", savedUser.getUsername());
        
        return convertToUserDto(savedUser);
    }
    
    /**
     * 사용자 로그인
     */
    @Transactional
    public LoginResponse login(LoginRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("사용자 로그인 시도: {}", request.getUsername());
        
        // 사용자 조회 및 상태 확인
        User user = userRepository.findActiveUserByUsername(request.getUsername(), LocalDateTime.now())
            .orElseThrow(() -> new CustomAuthenticationException("사용자계정 또는 비밀번호가 올바르지 않습니다."));
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            user.incrementLoginAttempts();
            userRepository.save(user);
            throw new CustomAuthenticationException("사용자계정 또는 비밀번호가 올바르지 않습니다.");
        }
        
        // 계정 잠금 확인
        if (user.isLocked()) {
            throw new CustomAuthenticationException("계정이 잠겨있습니다. 잠금 해제까지 기다려주세요.");
        }
        
        // 로그인 성공 처리
        user.updateLastLogin();
        userRepository.save(user);
        
        // JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        
        // 사용자 세션 생성
        createUserSession(user, request.getDeviceInfo(), accessToken);
        
        // 응답 데이터 구성
        LoginResponse.Builder loginBuilder = LoginResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(86400L) // 24시간
            .user(convertToUserDto(user));
        passwordPolicyService.applyToLoginResponse(loginBuilder, user);
        LoginResponse loginResponse = loginBuilder.build();
        
        log.info("사용자 로그인 성공: {}", user.getUsername());
        
        return loginResponse;
    }
    
    /**
     * 토큰 갱신
     */
    @Transactional
    public LoginResponse refreshToken(String refreshToken, jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("토큰 갱신 요청");
        
        // 리프레시 토큰 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomAuthenticationException("유효하지 않은 리프레시 토큰입니다.");
        }
        
        // 사용자 ID 추출
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 사용자 상태 확인
        if (!user.isActive()) {
            throw new CustomAuthenticationException("비활성화된 사용자입니다.");
        }
        
        // 새로운 토큰 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);
        
        // 기존 세션 업데이트
        updateUserSession(user, newAccessToken);
        
        // 응답 데이터 구성
        LoginResponse.Builder loginBuilder = LoginResponse.builder()
            .accessToken(newAccessToken)
            .refreshToken(newRefreshToken)
            .expiresIn(86400L)
            .user(convertToUserDto(user));
        passwordPolicyService.applyToLoginResponse(loginBuilder, user);
        LoginResponse loginResponse = loginBuilder.build();
        
        log.info("토큰 갱신 완료: {}", user.getUsername());
        
        return loginResponse;
    }
    
    /**
     * 로그아웃
     */
    @Transactional
    public void logout(jakarta.servlet.http.HttpServletRequest httpRequest) {
        log.info("사용자 로그아웃 요청");
        
        // TODO: HttpServletRequest에서 사용자 ID와 토큰 추출하여 세션 무효화
        // 현재는 간단히 로그만 출력
        
        log.info("사용자 로그아웃 완료");
    }
    
    /**
     * 사용자 세션 생성
     */
    private void createUserSession(User user, String deviceInfo, String accessToken) {
        UserSession session = UserSession.builder()
            .user(user)
            .sessionId(UUID.randomUUID().toString())
            .accessToken(accessToken)
            .deviceInfo(deviceInfo)
            .ipAddress("127.0.0.1") // TODO: 실제 IP 주소 추출
            .isActive(true)
            .build();
        
        userSessionRepository.save(session);
    }
    
    /**
     * 사용자 세션 업데이트
     */
    private void updateUserSession(User user, String newAccessToken) {
        userSessionRepository.findByUserIdAndIsActiveTrue(user.getId())
            .ifPresent(session -> {
                session.setAccessToken(newAccessToken);
                session.setLastActivityAt(LocalDateTime.now());
                userSessionRepository.save(session);
            });
    }
    
    /**
     * 사용자 세션 무효화
     */
    private void invalidateUserSession(Long userId, String accessToken) {
        userSessionRepository.findByUserIdAndAccessTokenAndIsActiveTrue(userId, accessToken)
            .ifPresent(session -> {
                session.setIsActive(false);
                session.setLoggedOutAt(LocalDateTime.now());
                userSessionRepository.save(session);
            });
    }
    
    /**
     * 사용자 프로필 정보 업데이트
     */
    @Transactional
    public UserInfo updateUserProfile(Long userId, String name, String nickname, String email) {
        log.info("사용자 프로필 업데이트 시작: userId={}", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 이메일 중복 확인 (다른 사용자가 사용 중인지)
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new CustomAuthenticationException("이미 사용 중인 이메일입니다.");
            }
        }
        
        // 프로필 정보 업데이트
        if (name != null && !name.trim().isEmpty()) {
            user.setName(name.trim());
        }
        if (nickname != null && !nickname.trim().isEmpty()) {
            user.setNickname(nickname.trim());
        }
        if (email != null && !email.trim().isEmpty()) {
            user.setEmail(email.trim());
        }
        
        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료: userId={}", userId);
        
        return convertToUserDto(updatedUser);
    }
    
    /**
     * 비밀번호 변경
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        log.info("비밀번호 변경 시작: userId={}", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 현재 비밀번호 확인
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new CustomAuthenticationException("비밀번호가 설정되지 않은 계정입니다. (WebAuthn 전용 계정)");
        }
        
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CustomAuthenticationException("현재 비밀번호가 일치하지 않습니다.");
        }
        
        // 새 비밀번호 검증
        if (newPassword == null || newPassword.length() < 8) {
            throw new CustomAuthenticationException("새 비밀번호는 8자 이상이어야 합니다.");
        }
        
        passwordPolicyService.validateNewPasswordNotReusingLastThree(user, newPassword);
        String oldEncoded = user.getPassword();
        String newEncoded = passwordEncoder.encode(newPassword);
        passwordPolicyService.recordPasswordRotation(user, oldEncoded, newEncoded);
        
        log.info("비밀번호 변경 완료: userId={}", userId);
    }

    /**
     * 비밀번호 변경 권고 3개월 유예
     */
    @Transactional
    public void deferPasswordChange(Long userId) {
        passwordPolicyService.deferPasswordChange(userId);
    }
    
    /**
     * User 엔티티를 DTO로 변환
     */
    /**
     * 사용자 정보 조회
     */
    public UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        return convertToUserDto(user);
    }
    
    private UserInfo convertToUserDto(User user) {
        return UserInfo.builder()
            .id(user.getId())
            .username(user.getUsername())
            .name(user.getName())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .build();
    }
} 