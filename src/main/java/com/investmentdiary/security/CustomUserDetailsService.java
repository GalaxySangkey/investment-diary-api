package com.investmentdiary.security;

import com.investmentdiary.entity.User;
import com.investmentdiary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {
    
    private static final Logger log = LoggerFactory.getLogger(CustomUserDetailsService.class);
    
    private final UserRepository userRepository;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("사용자 인증 정보 로드: {}", username);
        
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
        
        // 사용자 상태 확인
        if (!user.isActive()) {
            log.warn("비활성 사용자 로그인 시도: {}", username);
            throw new UsernameNotFoundException("비활성 사용자입니다: " + username);
        }
        
        // 계정 잠금 확인
        if (user.isLocked()) {
            log.warn("잠긴 계정 로그인 시도: {}", username);
            throw new UsernameNotFoundException("계정이 잠겨있습니다: " + username);
        }
        
        return createUserDetails(user);
    }
    
    /**
     * UserDetails 객체 생성
     */
    private UserDetails createUserDetails(User user) {
        // WebAuthn 사용자는 password가 null일 수 있으므로, null이면 빈 문자열 사용
        // 비밀번호 로그인에서는 password가 필수이지만, 여기서는 UserDetails 생성만 하므로 안전함
        String password = user.getPassword() != null ? user.getPassword() : "";
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(password)
            .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
            .accountExpired(false)
            .accountLocked(user.isLocked())
            .credentialsExpired(false)
            .disabled(!user.isActive())
            .build();
    }
} 