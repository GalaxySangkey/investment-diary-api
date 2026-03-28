package com.investmentdiary.security;

import com.investmentdiary.entity.User;
import com.investmentdiary.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserRepository userRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = getJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt)) {
                log.debug("JWT 토큰 발견: 길이={}", jwt.length());
                
                if (jwtTokenProvider.validateToken(jwt)) {
                    Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                    String username = jwtTokenProvider.getUsernameFromToken(jwt);
                    String role = jwtTokenProvider.getRoleFromToken(jwt);
                    
                    log.debug("토큰에서 추출한 정보: userId={}, username={}, role={}", userId, username, role);
                    
                    // 사용자 정보 조회
                    User user = userRepository.findById(userId)
                        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + userId));
                    
                    // 사용자 상태 확인
                    if (!user.isActive()) {
                        log.warn("비활성 사용자 접근 시도: {}", username);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    
                    // 역할 확인 (토큰의 role과 DB의 role 모두 확인)
                    String finalRole = role != null ? role : user.getRole().name();
                    String authority = "ROLE_" + finalRole;
                    
                    log.debug("최종 권한 설정: role={}, authority={}", finalRole, authority);
                    
                    // 인증 객체 생성
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        createUserDetails(user),
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority(authority))
                    );
                    
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    
                    // SecurityContext에 인증 정보 설정
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    log.debug("사용자 인증 성공: {} (ID: {}), 권한: {}", username, userId, authority);
                } else {
                    log.warn("JWT 토큰 검증 실패");
                }
            } else {
                log.debug("JWT 토큰을 찾을 수 없음");
            }
        } catch (Exception e) {
            log.error("JWT 토큰 처리 중 오류 발생: {}", e.getMessage(), e);
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 요청에서 JWT 토큰 추출
     * 우선순위: 1. 쿠키 2. Authorization 헤더 (하위 호환성)
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. 쿠키에서 토큰 읽기
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            log.info("쿠키 개수: {}", cookies.length);
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                log.info("쿠키 이름: {}, 값 길이: {}", cookie.getName(), cookie.getValue() != null ? cookie.getValue().length() : 0);
                if ("accessToken".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (StringUtils.hasText(token)) {
                        log.info("쿠키에서 accessToken 발견: 길이={}", token.length());
                        return token;
                    }
                }
            }
        } else {
            log.warn("쿠키가 없음 - 요청 URI: {}, Origin: {}", request.getRequestURI(), request.getHeader("Origin"));
        }
        
        // 2. Authorization 헤더에서 토큰 읽기 (하위 호환성)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            log.info("Authorization 헤더에서 토큰 발견");
            return bearerToken.substring(7);
        }
        
        log.warn("토큰을 찾을 수 없음 - 요청 URI: {}, Origin: {}", request.getRequestURI(), request.getHeader("Origin"));
        return null;
    }
    
    /**
     * UserDetails 객체 생성
     */
    private UserDetails createUserDetails(User user) {
        // WebAuthn 사용자는 password가 null일 수 있으므로, null이면 빈 문자열 사용
        // JWT 인증에서는 password를 실제로 사용하지 않으므로 안전함
        String password = user.getPassword() != null ? user.getPassword() : "";
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(password)
            .authorities("ROLE_" + user.getRole().name())
            .accountExpired(false)
            .accountLocked(user.isLocked())
            .credentialsExpired(false)
            .disabled(!user.isActive())
            .build();
    }
} 