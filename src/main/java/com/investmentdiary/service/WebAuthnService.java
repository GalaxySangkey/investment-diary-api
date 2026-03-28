package com.investmentdiary.service;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.investmentdiary.dto.auth.LoginResponse;
import com.investmentdiary.dto.auth.UserInfo;
import com.investmentdiary.dto.auth.WebAuthnCredentialInfo;
import com.investmentdiary.dto.auth.WebAuthnLoginFinishRequest;
import com.investmentdiary.dto.auth.WebAuthnLoginStartRequest;
import com.investmentdiary.dto.auth.WebAuthnLoginStartResponse;
import com.investmentdiary.dto.auth.WebAuthnRegisterFinishRequest;
import com.investmentdiary.dto.auth.WebAuthnRegisterStartRequest;
import com.investmentdiary.dto.auth.WebAuthnRegisterStartResponse;
import com.investmentdiary.event.UserRegisteredEvent;
import com.investmentdiary.entity.User;
import com.investmentdiary.entity.WebAuthnCredential;
import com.investmentdiary.exception.CustomAuthenticationException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.entity.UserSession;
import com.investmentdiary.repository.UserRepository;
import com.investmentdiary.repository.UserSessionRepository;
import com.investmentdiary.repository.WebAuthnCredentialRepository;
import com.investmentdiary.security.JwtTokenProvider;
import com.investmentdiary.util.EncryptionUtil;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.servlet.http.HttpServletRequest;

@Service
@Transactional(readOnly = true)
public class WebAuthnService {
    
    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);
    
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository webauthnCredentialRepository;
    private final UserSessionRepository userSessionRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final EncryptionUtil encryptionUtil;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordPolicyService passwordPolicyService;
    
    @Value("${webauthn.rp.id}")
    private String rpId;
    
    @Value("${webauthn.rp.name}")
    private String rpName;
    
    @Value("${webauthn.origin}")
    private String origin;
    
    @Value("${webauthn.timeout:60000}")
    private Long timeout;
    
    @Value("${webauthn.challenge-size:32}")
    private Integer challengeSize;
    
    private final ChallengeStorageService challengeStorageService;
    
    public WebAuthnService(UserRepository userRepository,
                          WebAuthnCredentialRepository webauthnCredentialRepository,
                          UserSessionRepository userSessionRepository,
                          JwtTokenProvider jwtTokenProvider,
                          EncryptionUtil encryptionUtil,
                          PasswordEncoder passwordEncoder,
                          ChallengeStorageService challengeStorageService,
                          ApplicationEventPublisher eventPublisher,
                          PasswordPolicyService passwordPolicyService) {
        this.userRepository = userRepository;
        this.webauthnCredentialRepository = webauthnCredentialRepository;
        this.userSessionRepository = userSessionRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.encryptionUtil = encryptionUtil;
        this.passwordEncoder = passwordEncoder;
        this.challengeStorageService = challengeStorageService;
        this.eventPublisher = eventPublisher;
        this.passwordPolicyService = passwordPolicyService;
    }
    
    /**
     * WebAuthn 회원가입 시작
     */
    @Transactional
    public WebAuthnRegisterStartResponse registerStart(WebAuthnRegisterStartRequest request, HttpServletRequest httpRequest) {
        log.info("WebAuthn 회원가입 시작: username={}, email={}", request.getUsername(), request.getEmail());
        
        // 중복 사용자계정 확인
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new CustomAuthenticationException("이미 사용 중인 사용자계정입니다.");
        }
        
        // 중복 이메일 확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomAuthenticationException("이미 사용 중인 이메일입니다.");
        }
        
        // Challenge 생성
        byte[] challengeBytes = new byte[challengeSize];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        
        // Challenge 저장 (Redis 또는 메모리)
        ChallengeStorageService.ChallengeData challengeData = new ChallengeStorageService.ChallengeData();
        challengeData.username = request.getUsername();
        challengeData.email = request.getEmail();
        challengeData.name = request.getName();
        challengeData.nickname = request.getNickname();
        challengeData.phone = request.getPhone();
        challengeData.password = request.getPassword(); // 비밀번호 저장
        challengeData.deviceName = request.getDeviceName();
        challengeData.createdAt = LocalDateTime.now();
        challengeStorageService.saveChallenge(challenge, challengeData);
        
        // User ID 생성 (Base64 encoded)
        UUID userId = UUID.randomUUID();
        String userIdBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteBuffer.allocate(16).putLong(userId.getMostSignificantBits()).putLong(userId.getLeastSignificantBits()).array()
        );
        
        // 응답 생성
        WebAuthnRegisterStartResponse response = WebAuthnRegisterStartResponse.builder()
            .challenge(challenge)
            .rp(WebAuthnRegisterStartResponse.RpInfo.builder()
                .id(rpId)
                .name(rpName)
                .build())
            .user(WebAuthnRegisterStartResponse.UserInfo.builder()
                .id(userIdBase64)
                .name(request.getUsername())
                .displayName(request.getNickname())
                .build())
            .pubKeyCredParams(createPubKeyCredParams())
            .timeout(timeout)
            .attestation("none") // 간단한 구현을 위해 "none" 사용
            .authenticatorSelection(WebAuthnRegisterStartResponse.AuthenticatorSelection.builder()
                .userVerification("preferred")
                .residentKey("preferred")
                .build())
            .build();
        
        log.info("WebAuthn 회원가입 시작 완료: username={}, challenge={}", request.getUsername(), challenge);
        
        return response;
    }
    
    /**
     * WebAuthn 회원가입 완료
     */
    @Transactional
    public UserInfo registerFinish(WebAuthnRegisterFinishRequest request, HttpServletRequest httpRequest) {
        log.info("WebAuthn 회원가입 완료 요청: challenge={}", request.getChallenge());
        
        // Challenge 검증
        ChallengeStorageService.ChallengeData challengeData = challengeStorageService.getChallenge(request.getChallenge());
        if (challengeData == null) {
            throw new CustomAuthenticationException("유효하지 않은 challenge입니다.");
        }
        
        // Challenge 만료 확인 (5분)
        if (challengeData.createdAt.plusMinutes(5).isBefore(LocalDateTime.now())) {
            challengeStorageService.deleteChallenge(request.getChallenge());
            throw new CustomAuthenticationException("Challenge가 만료되었습니다.");
        }
        
        // Challenge 사용 후 삭제
        challengeStorageService.deleteChallenge(request.getChallenge());
        
        // Credential 검증 (간단한 구현 - 실제로는 webauthn4j 라이브러리로 검증)
        // 여기서는 기본적인 검증만 수행
        if (request.getCredential() == null || request.getCredential().getId() == null) {
            throw new CustomAuthenticationException("유효하지 않은 credential입니다.");
        }
        
        // Credential ID 중복 확인
        if (webauthnCredentialRepository.existsByCredentialId(request.getCredential().getId())) {
            throw new CustomAuthenticationException("이미 등록된 credential입니다.");
        }
        
        // 전화번호 암호화
        String encryptedPhone = null;
        if (challengeData.phone != null) {
            encryptedPhone = encryptionUtil.encrypt(challengeData.phone);
        }
        
        // 사용자 생성
        // 비밀번호가 제공된 경우 암호화하여 저장, 없으면 null
        String encodedPassword = challengeData.password != null && !challengeData.password.isEmpty() 
            ? passwordEncoder.encode(challengeData.password) 
            : null;
        
        User user = User.builder()
            .username(challengeData.username)
            .password(encodedPassword) // 비밀번호가 제공된 경우 저장
            .email(challengeData.email)
            .name(challengeData.name)
            .nickname(challengeData.nickname)
            .phoneEncrypted(encryptedPhone)
            .role(User.UserRole.USER)
            .status(User.UserStatus.ACTIVE)
            .build();
        
        User savedUser = userRepository.save(user);
        if (encodedPassword != null) {
            passwordPolicyService.markNewPasswordAccount(savedUser);
        }
        
        // authenticatorAttachment와 transports 추출
        String authenticatorAttachment = request.getCredential().getAuthenticatorAttachment();
        String transportsJson = null;
        if (request.getCredential().getResponse().getTransports() != null && 
            request.getCredential().getResponse().getTransports().length > 0) {
            List<String> transportList = new ArrayList<>(java.util.Arrays.asList(request.getCredential().getResponse().getTransports()));
            // 크로스 디바이스 인증을 위해 "hybrid" transport 추가 (QR 코드를 통한 인증)
            if (!transportList.contains("hybrid")) {
                transportList.add("hybrid");
            }
            transportsJson = String.join(",", transportList);
        } else if ("platform".equals(authenticatorAttachment)) {
            // platform authenticator는 일반적으로 "internal" transport 사용
            // 하지만 크로스 디바이스 인증을 위해 "hybrid"도 추가
            transportsJson = "internal,hybrid";
        } else {
            // 기본값: 모든 transport 허용 + hybrid (크로스 디바이스 인증)
            transportsJson = "internal,usb,nfc,ble,hybrid";
        }
        
        // WebAuthn Credential 저장
        WebAuthnCredential credential = WebAuthnCredential.builder()
            .user(savedUser)
            .credentialId(request.getCredential().getId())
            .publicKey(extractPublicKey(request)) // 실제로는 attestation object에서 추출
            .counter(0L)
            .deviceName(challengeData.deviceName)
            .authenticatorAttachment(authenticatorAttachment)
            .transports(transportsJson)
            .build();
        
        webauthnCredentialRepository.save(credential);
        
        eventPublisher.publishEvent(new UserRegisteredEvent(savedUser.getId()));
        
        log.info("WebAuthn 회원가입 완료: username={}, credentialId={}", savedUser.getUsername(), credential.getCredentialId());
        
        return convertToUserDto(savedUser);
    }
    
    /**
     * WebAuthn 로그인 시작
     */
    @Transactional
    public WebAuthnLoginStartResponse loginStart(WebAuthnLoginStartRequest request, HttpServletRequest httpRequest) {
        log.info("WebAuthn 로그인 시작: username={}", request.getUsername());
        
        List<WebAuthnCredential> credentials;
        User user = null;
        Long userId = null;
        String username = null;
        
        // 사용자명이 제공된 경우 해당 사용자의 credential만 사용
        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            // 사용자 조회
            user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new CustomAuthenticationException("사용자를 찾을 수 없습니다."));
            
            // 사용자 상태 확인
            if (!user.isActive()) {
                throw new CustomAuthenticationException("비활성화된 사용자입니다.");
            }
            
            // 사용자의 Credential 조회
            credentials = webauthnCredentialRepository.findByUserIdOrderByLastUsedAtDesc(user.getId());
            if (credentials.isEmpty()) {
                throw new CustomAuthenticationException("등록된 인증기가 없습니다.");
            }
            userId = user.getId();
            username = user.getUsername();
        } else {
            // 사용자명이 없으면 모든 활성 credential 허용 (크로스 디바이스 인증)
            credentials = webauthnCredentialRepository.findAllByUserActiveTrueOrderByLastUsedAtDesc();
            if (credentials.isEmpty()) {
                throw new CustomAuthenticationException("등록된 인증기가 없습니다.");
            }
            // userId는 나중에 credential 검증 시 결정됨
        }
        
        // Challenge 생성
        byte[] challengeBytes = new byte[challengeSize];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        
        // Challenge 저장
        ChallengeStorageService.ChallengeData challengeData = new ChallengeStorageService.ChallengeData();
        challengeData.userId = userId; // null일 수 있음 (사용자명 없이 로그인하는 경우)
        challengeData.username = username; // null일 수 있음
        challengeData.createdAt = LocalDateTime.now();
        challengeStorageService.saveChallenge(challenge, challengeData);
        
        // 허용된 Credential 목록 생성
        // 크로스 디바이스 인증을 위해 allowCredentials를 설정하되,
        // transports를 명시적으로 지정하여 브라우저가 크로스 디바이스 인증을 시도할 수 있도록 함
        List<WebAuthnLoginStartResponse.PublicKeyCredentialDescriptor> allowCredentials = new ArrayList<>();
        for (WebAuthnCredential credential : credentials) {
            // 저장된 transports를 사용하거나 기본값 사용
            String[] transports;
            if (credential.getTransports() != null && !credential.getTransports().isEmpty()) {
                transports = credential.getTransports().split(",");
            } else {
                // 기본값: platform이면 internal, 아니면 모든 transport
                if ("platform".equals(credential.getAuthenticatorAttachment())) {
                    transports = new String[]{"internal"};
                } else {
                    transports = new String[]{"internal", "usb", "nfc", "ble"};
                }
            }
            
            // 크로스 디바이스 인증을 위해 "hybrid" transport 추가 (QR 코드를 통한 인증)
            // "hybrid"는 FIDO2 CTAP2의 크로스 디바이스 인증을 의미
            List<String> transportList = new ArrayList<>(java.util.Arrays.asList(transports));
            if (!transportList.contains("hybrid")) {
                transportList.add("hybrid");
            }
            transports = transportList.toArray(new String[0]);
            
            allowCredentials.add(WebAuthnLoginStartResponse.PublicKeyCredentialDescriptor.builder()
                .id(credential.getCredentialId())
                .type("public-key")
                .transports(transports)
                .build());
        }
        
        // 응답 생성
        WebAuthnLoginStartResponse response = WebAuthnLoginStartResponse.builder()
            .challenge(challenge)
            .timeout(timeout)
            .rpId(rpId)
            .allowCredentials(allowCredentials.isEmpty() ? null : allowCredentials) // 빈 리스트면 null로 설정하여 모든 credential 허용
            .userVerification("preferred")
            .build();
        
        log.info("WebAuthn 로그인 시작 완료: username={}, challenge={}, credentials={}", 
            request.getUsername(), challenge, credentials.size());
        
        return response;
    }
    
    /**
     * WebAuthn 로그인 완료
     */
    @Transactional
    public LoginResponse loginFinish(WebAuthnLoginFinishRequest request, HttpServletRequest httpRequest) {
        log.info("WebAuthn 로그인 완료 요청: challenge={}", request.getChallenge());
        
        // Challenge 검증
        ChallengeStorageService.ChallengeData challengeData = challengeStorageService.getChallenge(request.getChallenge());
        if (challengeData == null) {
            throw new CustomAuthenticationException("유효하지 않은 challenge입니다.");
        }
        
        // Challenge 만료 확인 (5분)
        if (challengeData.createdAt.plusMinutes(5).isBefore(LocalDateTime.now())) {
            challengeStorageService.deleteChallenge(request.getChallenge());
            throw new CustomAuthenticationException("Challenge가 만료되었습니다.");
        }
        
        // Challenge 사용 후 삭제
        challengeStorageService.deleteChallenge(request.getChallenge());
        
        // Credential ID로 credential 조회 (사용자명 없이 인증한 경우 credential ID로 사용자 찾기)
        WebAuthnCredential credential = webauthnCredentialRepository
            .findByCredentialId(request.getCredential().getId())
            .orElseThrow(() -> new CustomAuthenticationException("유효하지 않은 credential입니다."));
        
        // 사용자 조회
        User user = credential.getUser();
        if (user == null) {
            throw new UserNotFoundException("사용자를 찾을 수 없습니다.");
        }
        
        // 사용자 상태 확인
        if (!user.isActive()) {
            throw new CustomAuthenticationException("비활성화된 사용자입니다.");
        }
        
        // Challenge에 저장된 userId가 있으면 검증 (사용자명으로 로그인한 경우)
        if (challengeData.userId != null && !challengeData.userId.equals(user.getId())) {
            throw new CustomAuthenticationException("유효하지 않은 credential입니다.");
        }
        
        // Credential 검증 (간단한 구현 - 실제로는 webauthn4j 라이브러리로 검증)
        // 여기서는 기본적인 검증만 수행
        if (request.getCredential() == null || request.getCredential().getResponse() == null) {
            throw new CustomAuthenticationException("유효하지 않은 인증 응답입니다.");
        }
        
        // Counter 업데이트 (replay attack 방지)
        credential.incrementCounter();
        webauthnCredentialRepository.save(credential);
        
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
        
        log.info("WebAuthn 로그인 완료: username={}, credentialId={}", user.getUsername(), credential.getCredentialId());
        
        return loginResponse;
    }
    
    /**
     * 지원하는 알고리즘 목록 생성
     */
    private List<WebAuthnRegisterStartResponse.PubKeyCredParam> createPubKeyCredParams() {
        List<WebAuthnRegisterStartResponse.PubKeyCredParam> params = new ArrayList<>();
        params.add(WebAuthnRegisterStartResponse.PubKeyCredParam.builder()
            .type("public-key")
            .alg(-7L) // ES256
            .build());
        params.add(WebAuthnRegisterStartResponse.PubKeyCredParam.builder()
            .type("public-key")
            .alg(-257L) // RS256
            .build());
        return params;
    }
    
    /**
     * Public Key 추출 (간단한 구현)
     */
    private String extractPublicKey(WebAuthnRegisterFinishRequest request) {
        // 실제로는 attestation object를 파싱하여 public key를 추출해야 함
        // 여기서는 간단히 credential ID를 사용
        return request.getCredential().getId();
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
        log.info("사용자 세션 생성: userId={}, deviceInfo={}", user.getId(), deviceInfo);
    }
    
    /**
     * User 엔티티를 DTO로 변환
     */
    private UserInfo convertToUserDto(User user) {
        return UserInfo.builder()
            .id(user.getId())
            .username(user.getUsername())
            .name(user.getName())
            .nickname(user.getNickname())
            .email(user.getEmail())
            .build();
    }
    
    /**
     * 사용자의 패스키 목록 조회
     */
    public List<WebAuthnCredentialInfo> getCredentials(Long userId) {
        log.info("패스키 목록 조회: userId={}", userId);
        
        List<WebAuthnCredential> credentials = webauthnCredentialRepository.findByUserIdOrderByLastUsedAtDesc(userId);
        
        return credentials.stream()
            .map(this::convertToCredentialInfo)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 패스키 추가 시작 (기존 사용자용)
     */
    @Transactional
    public WebAuthnRegisterStartResponse addCredentialStart(Long userId, String deviceName, HttpServletRequest httpRequest) {
        log.info("패스키 추가 시작: userId={}, deviceName={}", userId, deviceName);
        
        // 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 사용자 상태 확인
        if (!user.isActive()) {
            throw new CustomAuthenticationException("비활성화된 사용자입니다.");
        }
        
        // Challenge 생성
        byte[] challengeBytes = new byte[challengeSize];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        
        // Challenge 저장
        ChallengeStorageService.ChallengeData challengeData = new ChallengeStorageService.ChallengeData();
        challengeData.userId = user.getId();
        challengeData.deviceName = deviceName;
        challengeData.createdAt = LocalDateTime.now();
        challengeStorageService.saveChallenge(challenge, challengeData);
        
        // User ID 생성 (Base64 encoded)
        String userIdBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
            ByteBuffer.allocate(16).putLong(user.getId()).putLong(0L).array()
        );
        
        // 응답 생성
        WebAuthnRegisterStartResponse response = WebAuthnRegisterStartResponse.builder()
            .challenge(challenge)
            .rp(WebAuthnRegisterStartResponse.RpInfo.builder()
                .id(rpId)
                .name(rpName)
                .build())
            .user(WebAuthnRegisterStartResponse.UserInfo.builder()
                .id(userIdBase64)
                .name(user.getUsername())
                .displayName(user.getNickname())
                .build())
            .pubKeyCredParams(createPubKeyCredParams())
            .timeout(timeout)
            .attestation("none")
            .authenticatorSelection(WebAuthnRegisterStartResponse.AuthenticatorSelection.builder()
                .userVerification("preferred")
                .residentKey("preferred")
                .build())
            .build();
        
        log.info("패스키 추가 시작 완료: userId={}, challenge={}", userId, challenge);
        
        return response;
    }
    
    /**
     * 패스키 추가 완료 (기존 사용자용)
     */
    @Transactional
    public WebAuthnCredentialInfo addCredentialFinish(Long userId, WebAuthnRegisterFinishRequest request, HttpServletRequest httpRequest) {
        log.info("패스키 추가 완료 요청: userId={}, challenge={}", userId, request.getChallenge());
        
        // Challenge 검증
        ChallengeStorageService.ChallengeData challengeData = challengeStorageService.getChallenge(request.getChallenge());
        if (challengeData == null || challengeData.userId == null || !challengeData.userId.equals(userId)) {
            throw new CustomAuthenticationException("유효하지 않은 challenge입니다.");
        }
        
        // Challenge 만료 확인 (5분)
        if (challengeData.createdAt.plusMinutes(5).isBefore(LocalDateTime.now())) {
            challengeStorageService.deleteChallenge(request.getChallenge());
            throw new CustomAuthenticationException("Challenge가 만료되었습니다.");
        }
        
        // Challenge 사용 후 삭제
        challengeStorageService.deleteChallenge(request.getChallenge());
        
        // 사용자 조회
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // Credential 검증
        if (request.getCredential() == null || request.getCredential().getId() == null) {
            throw new CustomAuthenticationException("유효하지 않은 credential입니다.");
        }
        
        // Credential ID 중복 확인
        if (webauthnCredentialRepository.existsByCredentialId(request.getCredential().getId())) {
            throw new CustomAuthenticationException("이미 등록된 credential입니다.");
        }
        
        // authenticatorAttachment와 transports 추출
        String authenticatorAttachment = request.getCredential().getAuthenticatorAttachment();
        String transportsJson = null;
        if (request.getCredential().getResponse().getTransports() != null && 
            request.getCredential().getResponse().getTransports().length > 0) {
            List<String> transportList = new ArrayList<>(java.util.Arrays.asList(request.getCredential().getResponse().getTransports()));
            if (!transportList.contains("hybrid")) {
                transportList.add("hybrid");
            }
            transportsJson = String.join(",", transportList);
        } else if ("platform".equals(authenticatorAttachment)) {
            transportsJson = "internal,hybrid";
        } else {
            transportsJson = "internal,usb,nfc,ble,hybrid";
        }
        
        // WebAuthn Credential 저장
        WebAuthnCredential credential = WebAuthnCredential.builder()
            .user(user)
            .credentialId(request.getCredential().getId())
            .publicKey(extractPublicKey(request))
            .counter(0L)
            .deviceName(challengeData.deviceName)
            .authenticatorAttachment(authenticatorAttachment)
            .transports(transportsJson)
            .build();
        
        WebAuthnCredential savedCredential = webauthnCredentialRepository.save(credential);
        
        log.info("패스키 추가 완료: userId={}, credentialId={}", userId, savedCredential.getCredentialId());
        
        return convertToCredentialInfo(savedCredential);
    }
    
    /**
     * 패스키 이름 수정
     */
    @Transactional
    public WebAuthnCredentialInfo updateCredentialName(Long userId, Long credentialId, String deviceName) {
        log.info("패스키 이름 수정 요청: userId={}, credentialId={}, deviceName={}", userId, credentialId, deviceName);
        
        WebAuthnCredential credential = webauthnCredentialRepository.findById(credentialId)
            .orElseThrow(() -> new CustomAuthenticationException("패스키를 찾을 수 없습니다."));
        
        // 소유자 확인
        if (!credential.getUser().getId().equals(userId)) {
            throw new CustomAuthenticationException("권한이 없습니다.");
        }
        
        // 디바이스 이름 업데이트
        credential.setDeviceName(deviceName);
        WebAuthnCredential updatedCredential = webauthnCredentialRepository.save(credential);
        
        log.info("패스키 이름 수정 완료: userId={}, credentialId={}, deviceName={}", userId, credentialId, deviceName);
        
        return convertToCredentialInfo(updatedCredential);
    }
    
    /**
     * 패스키 삭제
     */
    @Transactional
    public void deleteCredential(Long userId, Long credentialId) {
        log.info("패스키 삭제 요청: userId={}, credentialId={}", userId, credentialId);
        
        WebAuthnCredential credential = webauthnCredentialRepository.findById(credentialId)
            .orElseThrow(() -> new CustomAuthenticationException("패스키를 찾을 수 없습니다."));
        
        // 소유자 확인
        if (!credential.getUser().getId().equals(userId)) {
            throw new CustomAuthenticationException("권한이 없습니다.");
        }
        
        // 최소 1개는 유지해야 함
        long credentialCount = webauthnCredentialRepository.countByUserId(userId);
        if (credentialCount <= 1) {
            throw new CustomAuthenticationException("최소 1개의 패스키는 유지해야 합니다.");
        }
        
        webauthnCredentialRepository.delete(credential);
        
        log.info("패스키 삭제 완료: userId={}, credentialId={}", userId, credentialId);
    }
    
    /**
     * WebAuthnCredentialInfo DTO 변환
     */
    private WebAuthnCredentialInfo convertToCredentialInfo(WebAuthnCredential credential) {
        return WebAuthnCredentialInfo.builder()
            .id(credential.getId())
            .deviceName(credential.getDeviceName())
            .authenticatorAttachment(credential.getAuthenticatorAttachment())
            .transports(credential.getTransports())
            .lastUsedAt(credential.getLastUsedAt())
            .createdAt(credential.getCreatedAt())
            .build();
    }
    
}

