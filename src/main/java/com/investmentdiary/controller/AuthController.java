package com.investmentdiary.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseCookie;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.dto.auth.LoginRequest;
import com.investmentdiary.dto.auth.LoginResponse;
import com.investmentdiary.dto.auth.RegisterRequest;
import com.investmentdiary.dto.auth.UserInfo;
import com.investmentdiary.dto.auth.WebAuthnCredentialInfo;
import com.investmentdiary.dto.auth.WebAuthnLoginFinishRequest;
import com.investmentdiary.dto.auth.WebAuthnLoginStartRequest;
import com.investmentdiary.dto.auth.WebAuthnLoginStartResponse;
import com.investmentdiary.dto.auth.WebAuthnRegisterFinishRequest;
import com.investmentdiary.dto.auth.WebAuthnRegisterStartRequest;
import com.investmentdiary.dto.auth.WebAuthnRegisterStartResponse;
import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.exception.CustomAuthenticationException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.service.AuthService;
import com.investmentdiary.service.WebAuthnService;
import com.investmentdiary.security.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "사용자 인증 관련 API")
public class AuthController {
    
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final WebAuthnService webauthnService;
    private final JwtTokenProvider jwtTokenProvider;
    
    public AuthController(AuthService authService, WebAuthnService webauthnService, JwtTokenProvider jwtTokenProvider) {
        this.authService = authService;
        this.webauthnService = webauthnService;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * 요청에서 JWT 토큰 추출
     * 우선순위: 1. 쿠키 2. Authorization 헤더 (하위 호환성)
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. 쿠키에서 토큰 읽기
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (StringUtils.hasText(token)) {
                        return token;
                    }
                }
            }
        }
        
        // 2. Authorization 헤더에서 토큰 읽기 (하위 호환성)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 요청에서 사용자 ID 추출
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);
        if (jwt != null) {
            return jwtTokenProvider.getUserIdFromToken(jwt);
        }
        throw new IllegalArgumentException("JWT 토큰을 찾을 수 없습니다.");
    }
    
    @PostMapping("/register")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    public UnifiedApiResponse<UserInfo> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("회원가입 요청 받음: username={}, email={}, nickname={}", 
                    request.getUsername(), request.getEmail(), request.getNickname());
            
            UserInfo userInfo = authService.register(request, httpRequest);
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(true)
                    .code(ResponseCode.CREATED)
                    .message("회원가입이 완료되었습니다.")
                    .data(userInfo)
                    .count(1) // 단일 사용자 생성
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("회원가입 성공 응답 반환: username={}, success={}, code={}", 
                    request.getUsername(), response.isSuccess(), response.getCode());
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("회원가입 실패 (인증 오류): username={}, error={}", request.getUsername(), e.getMessage());
            
            // 에러 메시지에 따라 다른 코드 사용
            String errorCode = ResponseCode.REGISTER_FAILED;
            String errorField = "username";
            
            if (e.getMessage().contains("사용자계정")) {
                errorCode = ResponseCode.DUPLICATE_USERNAME;
                errorField = "username";
            } else if (e.getMessage().contains("이메일")) {
                errorCode = ResponseCode.DUPLICATE_EMAIL;
                errorField = "email";
            }
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(errorCode)
                    .message(e.getMessage())
                    .error(createErrorDetails(errorField, errorField.equals("username") ? request.getUsername() : request.getEmail(), errorCode))
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.error("회원가입 실패 응답 반환: username={}, success={}, code={}, message={}", 
                    request.getUsername(), response.isSuccess(), response.getCode(), response.getMessage());
            
            return response;
        } catch (ConstraintViolationException e) {
            List<Map<String, String>> violations = e.getConstraintViolations().stream()
                    .map(v -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("field", v.getPropertyPath() != null ? v.getPropertyPath().toString() : "");
                        row.put("message", v.getMessage());
                        return row;
                    })
                    .collect(Collectors.toList());
            String summary = e.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .distinct()
                    .collect(Collectors.joining(" "));

            Map<String, Object> errorDetails = new LinkedHashMap<>();
            errorDetails.put("code", ResponseCode.VALIDATION_FAILED);
            errorDetails.put("violations", violations);

            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.VALIDATION_FAILED)
                    .message(summary.isEmpty() ? "입력값을 확인해 주세요." : summary)
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();

            log.warn("회원가입 실패 (검증): username={}, violations={}", request.getUsername(), violations);
            return response;
        } catch (Exception e) {
            log.error("회원가입 실패 (기타 오류): username={}, error={}", request.getUsername(), e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("회원가입 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.error("회원가입 실패 응답 반환 (기타 오류): username={}, success={}, code={}, message={}", 
                    request.getUsername(), response.isSuccess(), response.getCode(), response.getMessage());
            
            return response;
        }
    }
    
    // 에러 상세 정보 생성 헬퍼 메서드
    private java.util.Map<String, Object> createErrorDetails(String field, String value, String reason) {
        java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
        errorDetails.put("field", field);
        errorDetails.put("value", value);
        errorDetails.put("reason", reason);
        return errorDetails;
    }
    
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인을 처리합니다.")
    public UnifiedApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        
        try {
            log.info("로그인 요청 받음: username={}", request.getUsername());
            
            LoginResponse loginResponse = authService.login(request, httpRequest);
            
            // JWT 토큰을 쿠키에 설정
            setTokenCookies(httpResponse, loginResponse.getAccessToken(), loginResponse.getRefreshToken());
            
            // 응답에서 토큰 제거 (보안상 쿠키에만 저장)
            LoginResponse responseWithoutTokens = LoginResponse.builder()
                    .accessToken(null)
                    .refreshToken(null)
                    .expiresIn(loginResponse.getExpiresIn())
                    .user(loginResponse.getUser())
                    .passwordChangeRecommended(loginResponse.isPasswordChangeRecommended())
                    .passwordChangeDueAt(loginResponse.getPasswordChangeDueAt())
                    .passwordChangeDeferredUntil(loginResponse.getPasswordChangeDeferredUntil())
                    .build();
            
            UnifiedApiResponse<LoginResponse> response = UnifiedApiResponse.<LoginResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("로그인이 완료되었습니다.")
                    .data(responseWithoutTokens)
                    .count(1) // 단일 로그인 세션
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("로그인 성공 응답 반환: username={}, success={}, code={}", 
                    request.getUsername(), response.isSuccess(), response.getCode());
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("로그인 실패 (인증 오류): username={}, error={}", request.getUsername(), e.getMessage());
            
            // 에러 메시지에 따라 다른 코드 사용
            String errorCode = ResponseCode.LOGIN_FAILED;
            if (e.getMessage().contains("계정이 잠겨")) {
                errorCode = ResponseCode.ACCOUNT_LOCKED;
            } else if (e.getMessage().contains("사용자계정") || e.getMessage().contains("비밀번호")) {
                errorCode = ResponseCode.INVALID_CREDENTIALS;
            }
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credentials");
            errorDetails.put("username", request.getUsername());
            errorDetails.put("reason", errorCode);
            errorDetails.put("message", e.getMessage());
            
            UnifiedApiResponse<LoginResponse> response = UnifiedApiResponse.<LoginResponse>builder()
                    .success(false)
                    .code(errorCode)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.error("로그인 실패 응답 반환: username={}, success={}, code={}, message={}", 
                    request.getUsername(), response.isSuccess(), response.getCode(), response.getMessage());
            
            return response;
        } catch (Exception e) {
            log.error("로그인 실패 (기타 오류): username={}, error={}", request.getUsername(), e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<LoginResponse> response = UnifiedApiResponse.<LoginResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("로그인 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.error("로그인 실패 응답 반환 (기타 오류): username={}, success={}, code={}, message={}", 
                    request.getUsername(), response.isSuccess(), response.getCode(), response.getMessage());
            
            return response;
        }
    }
    
    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "액세스 토큰을 갱신합니다.")
    public ApiResponse<LoginResponse> refreshToken(
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        
        // 쿠키에서 refresh token 읽기
        String refreshToken = getRefreshTokenFromCookie(httpRequest);
        if (refreshToken == null) {
            return ApiResponse.error(ResponseCode.AUTHENTICATION_FAILED, "Refresh token이 없습니다.");
        }
        
        LoginResponse response = authService.refreshToken("Bearer " + refreshToken, httpRequest);
        
        // 새로운 토큰을 쿠키에 설정
        setTokenCookies(httpResponse, response.getAccessToken(), response.getRefreshToken());
        
        // 응답에서 토큰 제거
        LoginResponse responseWithoutTokens = LoginResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .expiresIn(response.getExpiresIn())
                .user(response.getUser())
                .passwordChangeRecommended(response.isPasswordChangeRecommended())
                .passwordChangeDueAt(response.getPasswordChangeDueAt())
                .passwordChangeDeferredUntil(response.getPasswordChangeDeferredUntil())
                .build();
        
        return ApiResponse.success(responseWithoutTokens);
    }
    
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자 로그아웃을 처리합니다.")
    public ApiResponse<Void> logout(
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        authService.logout(httpRequest);
        
        // 쿠키 삭제
        clearTokenCookies(httpResponse);
        
        return ApiResponse.success(null, "로그아웃이 완료되었습니다.");
    }
    
    @GetMapping("/me")
    @Operation(summary = "현재 사용자 정보", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    public UnifiedApiResponse<UserInfo> getCurrentUser(HttpServletRequest httpRequest) {
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            UserInfo userInfo = authService.getUserInfo(userId);
            
            return UnifiedApiResponse.<UserInfo>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("사용자 정보 조회 성공")
                    .data(userInfo)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: {}", e.getMessage(), e);
            return UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.AUTHENTICATION_FAILED)
                    .message("사용자 정보를 조회할 수 없습니다.")
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
        }
    }
    
    /**
     * JWT 토큰을 쿠키에 설정
     */
    private void setTokenCookies(jakarta.servlet.http.HttpServletResponse response, 
                                 String accessToken, String refreshToken) {
        // Access Token 쿠키 설정 (ResponseCookie 사용하여 SameSite 설정)
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true) // XSS 방지
                .secure(false) // 개발 환경에서는 false, 프로덕션에서는 true
                .path("/")
                .maxAge(86400) // 24시간 (초)
                .sameSite("Lax") // CORS 요청에서도 쿠키 전송 가능
                .build();
        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        
        // Refresh Token 쿠키 설정
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // 개발 환경에서는 false, 프로덕션에서는 true
                .path("/")
                .maxAge(604800) // 7일 (초)
                .sameSite("Lax") // CORS 요청에서도 쿠키 전송 가능
                .build();
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());
    }
    
    /**
     * 쿠키에서 Access Token 읽기
     */
    private String getAccessTokenFromCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    
    /**
     * 쿠키에서 Refresh Token 읽기
     */
    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
    
    /**
     * 토큰 쿠키 삭제
     */
    private void clearTokenCookies(jakarta.servlet.http.HttpServletResponse response) {
        // Access Token 쿠키 삭제
        ResponseCookie accessTokenCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", accessTokenCookie.toString());
        
        // Refresh Token 쿠키 삭제
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", refreshTokenCookie.toString());
    }
    
    // ========== WebAuthn 엔드포인트 ==========
    
    @PostMapping("/webauthn/register/start")
    @Operation(summary = "WebAuthn 회원가입 시작", description = "WebAuthn 회원가입을 시작합니다. Challenge를 반환합니다.")
    public UnifiedApiResponse<WebAuthnRegisterStartResponse> webauthnRegisterStart(
            @Valid @RequestBody WebAuthnRegisterStartRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("WebAuthn 회원가입 시작 요청: username={}, email={}", request.getUsername(), request.getEmail());
            
            WebAuthnRegisterStartResponse response = webauthnService.registerStart(request, httpRequest);
            
            UnifiedApiResponse<WebAuthnRegisterStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnRegisterStartResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("WebAuthn 회원가입 시작이 완료되었습니다.")
                    .data(response)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("WebAuthn 회원가입 시작 성공: username={}", request.getUsername());
            
            return apiResponse;
        } catch (CustomAuthenticationException e) {
            log.error("WebAuthn 회원가입 시작 실패: username={}, error={}", request.getUsername(), e.getMessage());
            
            String errorCode = ResponseCode.REGISTER_FAILED;
            String errorField = "username";
            
            if (e.getMessage().contains("사용자계정")) {
                errorCode = ResponseCode.DUPLICATE_USERNAME;
                errorField = "username";
            } else if (e.getMessage().contains("이메일")) {
                errorCode = ResponseCode.DUPLICATE_EMAIL;
                errorField = "email";
            }
            
            UnifiedApiResponse<WebAuthnRegisterStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnRegisterStartResponse>builder()
                    .success(false)
                    .code(errorCode)
                    .message(e.getMessage())
                    .error(createErrorDetails(errorField, errorField.equals("username") ? request.getUsername() : request.getEmail(), errorCode))
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        } catch (Exception e) {
            log.error("WebAuthn 회원가입 시작 실패 (기타 오류): username={}, error={}", request.getUsername(), e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<WebAuthnRegisterStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnRegisterStartResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("WebAuthn 회원가입 시작 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }
    
    @PostMapping("/webauthn/register/finish")
    @Operation(summary = "WebAuthn 회원가입 완료", description = "WebAuthn 회원가입을 완료합니다. Credential을 검증하고 저장합니다.")
    public UnifiedApiResponse<UserInfo> webauthnRegisterFinish(
            @Valid @RequestBody WebAuthnRegisterFinishRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("WebAuthn 회원가입 완료 요청: challenge={}", request.getChallenge());
            
            UserInfo userInfo = webauthnService.registerFinish(request, httpRequest);
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(true)
                    .code(ResponseCode.CREATED)
                    .message("WebAuthn 회원가입이 완료되었습니다.")
                    .data(userInfo)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("WebAuthn 회원가입 완료 성공: username={}", userInfo.getUsername());
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("WebAuthn 회원가입 완료 실패: error={}", e.getMessage());
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.REGISTER_FAILED)
                    .message(e.getMessage())
                    .error(createErrorDetails("credential", request.getCredential() != null ? request.getCredential().getId() : "unknown", ResponseCode.REGISTER_FAILED))
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        } catch (Exception e) {
            log.error("WebAuthn 회원가입 완료 실패 (기타 오류): error={}", e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("WebAuthn 회원가입 완료 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        }
    }
    
    @PostMapping("/webauthn/login/start")
    @Operation(summary = "WebAuthn 로그인 시작", description = "WebAuthn 로그인을 시작합니다. Challenge를 반환합니다.")
    public UnifiedApiResponse<WebAuthnLoginStartResponse> webauthnLoginStart(
            @Valid @RequestBody WebAuthnLoginStartRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("WebAuthn 로그인 시작 요청: username={}", request.getUsername());
            
            WebAuthnLoginStartResponse response = webauthnService.loginStart(request, httpRequest);
            
            UnifiedApiResponse<WebAuthnLoginStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnLoginStartResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("WebAuthn 로그인 시작이 완료되었습니다.")
                    .data(response)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("WebAuthn 로그인 시작 성공: username={}", request.getUsername());
            
            return apiResponse;
        } catch (CustomAuthenticationException e) {
            log.error("WebAuthn 로그인 시작 실패: username={}, error={}", request.getUsername(), e.getMessage());
            
            String errorCode = ResponseCode.LOGIN_FAILED;
            if (e.getMessage().contains("비활성")) {
                errorCode = ResponseCode.ACCOUNT_LOCKED;
            }
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "username");
            errorDetails.put("username", request.getUsername());
            errorDetails.put("reason", errorCode);
            errorDetails.put("message", e.getMessage());
            
            UnifiedApiResponse<WebAuthnLoginStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnLoginStartResponse>builder()
                    .success(false)
                    .code(errorCode)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        } catch (Exception e) {
            log.error("WebAuthn 로그인 시작 실패 (기타 오류): username={}, error={}", request.getUsername(), e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<WebAuthnLoginStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnLoginStartResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("WebAuthn 로그인 시작 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }
    
    @PostMapping("/webauthn/login/finish")
    @Operation(summary = "WebAuthn 로그인 완료", description = "WebAuthn 로그인을 완료합니다. Credential을 검증하고 JWT 토큰을 발급합니다.")
    public UnifiedApiResponse<LoginResponse> webauthnLoginFinish(
            @Valid @RequestBody WebAuthnLoginFinishRequest request,
            HttpServletRequest httpRequest,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        
        try {
            log.info("WebAuthn 로그인 완료 요청: challenge={}", request.getChallenge());
            
            LoginResponse loginResponse = webauthnService.loginFinish(request, httpRequest);
            
            // JWT 토큰을 쿠키에 설정
            setTokenCookies(httpResponse, loginResponse.getAccessToken(), loginResponse.getRefreshToken());
            
            // 응답에서 토큰 제거
            LoginResponse responseWithoutTokens = LoginResponse.builder()
                    .accessToken(null)
                    .refreshToken(null)
                    .expiresIn(loginResponse.getExpiresIn())
                    .user(loginResponse.getUser())
                    .passwordChangeRecommended(loginResponse.isPasswordChangeRecommended())
                    .passwordChangeDueAt(loginResponse.getPasswordChangeDueAt())
                    .passwordChangeDeferredUntil(loginResponse.getPasswordChangeDeferredUntil())
                    .build();
            
            UnifiedApiResponse<LoginResponse> response = UnifiedApiResponse.<LoginResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("WebAuthn 로그인이 완료되었습니다.")
                    .data(responseWithoutTokens)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("WebAuthn 로그인 완료 성공: username={}", loginResponse.getUser().getUsername());
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("WebAuthn 로그인 완료 실패: error={}", e.getMessage());
            
            String errorCode = ResponseCode.LOGIN_FAILED;
            if (e.getMessage().contains("credential")) {
                errorCode = ResponseCode.INVALID_CREDENTIALS;
            }
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", errorCode);
            errorDetails.put("message", e.getMessage());
            
            UnifiedApiResponse<LoginResponse> response = UnifiedApiResponse.<LoginResponse>builder()
                    .success(false)
                    .code(errorCode)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        } catch (Exception e) {
            log.error("WebAuthn 로그인 완료 실패 (기타 오류): error={}", e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<LoginResponse> response = UnifiedApiResponse.<LoginResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("WebAuthn 로그인 완료 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        }
    }
    
    // ========== 패스키 관리 엔드포인트 ==========
    
    @GetMapping("/webauthn/credentials")
    @Operation(summary = "패스키 목록 조회", description = "현재 사용자의 등록된 패스키 목록을 조회합니다.")
    public UnifiedApiResponse<java.util.List<WebAuthnCredentialInfo>> getCredentials(
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            log.info("패스키 목록 조회 요청: userId={}", userId);
            
            java.util.List<WebAuthnCredentialInfo> credentials = webauthnService.getCredentials(userId);
            
            UnifiedApiResponse<java.util.List<WebAuthnCredentialInfo>> response = UnifiedApiResponse.<java.util.List<WebAuthnCredentialInfo>>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("패스키 목록 조회가 완료되었습니다.")
                    .data(credentials)
                    .count(credentials.size())
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("패스키 목록 조회 성공: userId={}, count={}", userId, credentials.size());
            
            return response;
        } catch (Exception e) {
            log.error("패스키 목록 조회 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credentials");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<java.util.List<WebAuthnCredentialInfo>> response = UnifiedApiResponse.<java.util.List<WebAuthnCredentialInfo>>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("패스키 목록 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        }
    }
    
    @PostMapping("/webauthn/credentials/add/start")
    @Operation(summary = "패스키 추가 시작", description = "새로운 패스키를 추가하기 위한 Challenge를 받습니다.")
    public UnifiedApiResponse<WebAuthnRegisterStartResponse> addCredentialStart(
            @RequestParam(value = "deviceName", required = false) String deviceName,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            log.info("패스키 추가 시작 요청: userId={}, deviceName={}", userId, deviceName);
            
            WebAuthnRegisterStartResponse response = webauthnService.addCredentialStart(
                userId, 
                deviceName != null ? deviceName : httpRequest.getHeader("User-Agent"),
                httpRequest
            );
            
            UnifiedApiResponse<WebAuthnRegisterStartResponse> apiResponse = UnifiedApiResponse.<WebAuthnRegisterStartResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("패스키 추가 시작이 완료되었습니다.")
                    .data(response)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("패스키 추가 시작 성공: userId={}", userId);
            
            return apiResponse;
        } catch (CustomAuthenticationException e) {
            log.error("패스키 추가 시작 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<WebAuthnRegisterStartResponse> response = UnifiedApiResponse.<WebAuthnRegisterStartResponse>builder()
                    .success(false)
                    .code(ResponseCode.REGISTER_FAILED)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        } catch (Exception e) {
            log.error("패스키 추가 시작 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<WebAuthnRegisterStartResponse> response = UnifiedApiResponse.<WebAuthnRegisterStartResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("패스키 추가 시작 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        }
    }
    
    @PostMapping("/webauthn/credentials/add/finish")
    @Operation(summary = "패스키 추가 완료", description = "새로운 패스키를 등록합니다.")
    public UnifiedApiResponse<WebAuthnCredentialInfo> addCredentialFinish(
            @Valid @RequestBody WebAuthnRegisterFinishRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            log.info("패스키 추가 완료 요청: userId={}, challenge={}", userId, request.getChallenge());
            
            WebAuthnCredentialInfo credentialInfo = webauthnService.addCredentialFinish(userId, request, httpRequest);
            
            UnifiedApiResponse<WebAuthnCredentialInfo> response = UnifiedApiResponse.<WebAuthnCredentialInfo>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("패스키 추가가 완료되었습니다.")
                    .data(credentialInfo)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("패스키 추가 완료 성공: userId={}, credentialId={}", userId, credentialInfo.getId());
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("패스키 추가 완료 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<WebAuthnCredentialInfo> response = UnifiedApiResponse.<WebAuthnCredentialInfo>builder()
                    .success(false)
                    .code(ResponseCode.REGISTER_FAILED)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        } catch (Exception e) {
            log.error("패스키 추가 완료 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<WebAuthnCredentialInfo> response = UnifiedApiResponse.<WebAuthnCredentialInfo>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("패스키 추가 완료 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        }
    }
    
    @PostMapping("/webauthn/credentials/{credentialId}/name/update")
    @Operation(summary = "패스키 이름 수정", description = "등록된 패스키의 디바이스 이름을 수정합니다. (POST 기반)")
    public UnifiedApiResponse<WebAuthnCredentialInfo> updateCredentialName(
            @PathVariable Long credentialId,
            @RequestBody java.util.Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            String deviceName = request.get("deviceName");
            
            if (deviceName == null || deviceName.trim().isEmpty()) {
                throw new CustomAuthenticationException("디바이스 이름을 입력해주세요.");
            }
            
            log.info("패스키 이름 수정 요청: userId={}, credentialId={}, deviceName={}", userId, credentialId, deviceName);
            
            WebAuthnCredentialInfo credentialInfo = webauthnService.updateCredentialName(userId, credentialId, deviceName.trim());
            
            UnifiedApiResponse<WebAuthnCredentialInfo> response = UnifiedApiResponse.<WebAuthnCredentialInfo>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("패스키 이름이 수정되었습니다.")
                    .data(credentialInfo)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("패스키 이름 수정 성공: userId={}, credentialId={}", userId, credentialId);
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("패스키 이름 수정 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.BAD_REQUEST);
            
            UnifiedApiResponse<WebAuthnCredentialInfo> apiResponse = UnifiedApiResponse.<WebAuthnCredentialInfo>builder()
                    .success(false)
                    .code(ResponseCode.BAD_REQUEST)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        } catch (Exception e) {
            log.error("패스키 이름 수정 실패 (기타 오류): error={}", e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<WebAuthnCredentialInfo> apiResponse = UnifiedApiResponse.<WebAuthnCredentialInfo>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("패스키 이름 수정 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }
    
    @PostMapping("/webauthn/credentials/{credentialId}/delete")
    @Operation(summary = "패스키 삭제", description = "등록된 패스키를 삭제합니다. 최소 1개의 패스키는 유지해야 합니다. (POST 기반)")
    public UnifiedApiResponse<Void> deleteCredential(
            @PathVariable Long credentialId,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            log.info("패스키 삭제 요청: userId={}, credentialId={}", userId, credentialId);
            
            webauthnService.deleteCredential(userId, credentialId);
            
            UnifiedApiResponse<Void> response = UnifiedApiResponse.<Void>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("패스키 삭제가 완료되었습니다.")
                    .data(null)
                    .count(0)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("패스키 삭제 성공: userId={}, credentialId={}", userId, credentialId);
            
            return response;
        } catch (CustomAuthenticationException e) {
            log.error("패스키 삭제 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<Void> response = UnifiedApiResponse.<Void>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        } catch (Exception e) {
            log.error("패스키 삭제 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("field", "credential");
            errorDetails.put("reason", e.getMessage());
            
            UnifiedApiResponse<Void> response = UnifiedApiResponse.<Void>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("패스키 삭제 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return response;
        }
    }
    
    @PostMapping("/user/profile/update")
    @Operation(summary = "사용자 프로필 수정", description = "사용자의 이름, 닉네임, 이메일을 수정합니다. (POST 기반)")
    public UnifiedApiResponse<UserInfo> updateUserProfile(
            @RequestBody java.util.Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            String name = request.get("name");
            String nickname = request.get("nickname");
            String email = request.get("email");
            
            log.info("사용자 프로필 수정 요청: userId={}, name={}, nickname={}, email={}", 
                    userId, name, nickname, email);
            
            UserInfo userInfo = authService.updateUserProfile(userId, name, nickname, email);
            
            UnifiedApiResponse<UserInfo> response = UnifiedApiResponse.<UserInfo>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("프로필이 성공적으로 수정되었습니다.")
                    .data(userInfo)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("사용자 프로필 수정 성공: userId={}", userId);
            
            return response;
        } catch (CustomAuthenticationException | UserNotFoundException e) {
            log.error("사용자 프로필 수정 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.BAD_REQUEST);
            
            UnifiedApiResponse<UserInfo> apiResponse = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.BAD_REQUEST)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        } catch (Exception e) {
            log.error("예상치 못한 예외 발생: ", e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", "프로필 수정 중 오류가 발생했습니다.");
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<UserInfo> apiResponse = UnifiedApiResponse.<UserInfo>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("프로필 수정 중 오류가 발생했습니다.")
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }
    
    @PostMapping("/user/password/update")
    @Operation(summary = "비밀번호 변경", description = "사용자의 비밀번호를 변경합니다. (POST 기반)")
    public UnifiedApiResponse<Void> changePassword(
            @RequestBody java.util.Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            String currentPassword = request.get("currentPassword");
            String newPassword = request.get("newPassword");
            
            if (currentPassword == null || currentPassword.trim().isEmpty()) {
                throw new CustomAuthenticationException("현재 비밀번호를 입력해주세요.");
            }
            
            if (newPassword == null || newPassword.trim().isEmpty()) {
                throw new CustomAuthenticationException("새 비밀번호를 입력해주세요.");
            }
            
            log.info("비밀번호 변경 요청: userId={}", userId);
            
            authService.changePassword(userId, currentPassword.trim(), newPassword.trim());
            
            UnifiedApiResponse<Void> response = UnifiedApiResponse.<Void>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("비밀번호가 성공적으로 변경되었습니다.")
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("비밀번호 변경 성공: userId={}", userId);
            
            return response;
        } catch (CustomAuthenticationException | UserNotFoundException e) {
            log.error("비밀번호 변경 실패: error={}", e.getMessage());
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.BAD_REQUEST);
            
            UnifiedApiResponse<Void> apiResponse = UnifiedApiResponse.<Void>builder()
                    .success(false)
                    .code(ResponseCode.BAD_REQUEST)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        } catch (Exception e) {
            log.error("예상치 못한 예외 발생: ", e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", "비밀번호 변경 중 오류가 발생했습니다.");
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<Void> apiResponse = UnifiedApiResponse.<Void>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("비밀번호 변경 중 오류가 발생했습니다.")
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }

    @PostMapping("/user/password/defer")
    @Operation(summary = "비밀번호 변경 유예", description = "비밀번호 변경 권고가 표시된 경우 3개월 유예를 적용합니다.")
    public UnifiedApiResponse<Void> deferPasswordChange(HttpServletRequest httpRequest) {
        try {
            Long userId = getUserIdFromRequest(httpRequest);
            log.info("비밀번호 변경 유예 요청: userId={}", userId);
            authService.deferPasswordChange(userId);
            return UnifiedApiResponse.<Void>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("비밀번호 변경이 3개월 유예되었습니다.")
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
        } catch (CustomAuthenticationException | UserNotFoundException e) {
            log.error("비밀번호 변경 유예 실패: error={}", e.getMessage());
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.BAD_REQUEST);
            return UnifiedApiResponse.<Void>builder()
                    .success(false)
                    .code(ResponseCode.BAD_REQUEST)
                    .message(e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
        } catch (Exception e) {
            log.error("비밀번호 변경 유예 중 오류: ", e);
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("message", "비밀번호 변경 유예 처리 중 오류가 발생했습니다.");
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            return UnifiedApiResponse.<Void>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("비밀번호 변경 유예 처리 중 오류가 발생했습니다.")
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
        }
    }
} 