package com.investmentdiary.exception;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.exception.CustomAuthenticationException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.exception.InvestmentNotFoundException;
import com.investmentdiary.exception.PortfolioNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 커스텀 인증 예외 처리
     */
    @ExceptionHandler(CustomAuthenticationException.class)
    public ResponseEntity<UnifiedApiResponse<Object>> handleCustomAuthenticationException(
            CustomAuthenticationException ex, WebRequest request) {
        
        log.error("커스텀 인증 예외 발생: {}", ex.getMessage());
        
        // 요청 URL에 따라 다른 상태 코드 및 에러 코드 반환
        String requestUrl = request.getDescription(false);
        HttpStatus status;
        String errorCode;
        Map<String, Object> errorDetails = new HashMap<>();
        
        if (requestUrl.contains("/register")) {
            status = HttpStatus.BAD_REQUEST;
            errorCode = ResponseCode.REGISTER_FAILED;
            
            // 회원가입 에러 상세 정보
            errorDetails.put("message", ex.getMessage());
            errorDetails.put("code", errorCode);
            if (ex.getMessage().contains("사용자계정")) {
                errorDetails.put("field", "username");
                errorDetails.put("reason", ResponseCode.DUPLICATE_USERNAME);
            } else if (ex.getMessage().contains("이메일")) {
                errorDetails.put("field", "email");
                errorDetails.put("reason", ResponseCode.DUPLICATE_EMAIL);
            }
        } else if (requestUrl.contains("/login")) {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = ResponseCode.LOGIN_FAILED;
            
            // 로그인 에러 상세 정보
            errorDetails.put("message", ex.getMessage());
            errorDetails.put("code", errorCode);
            errorDetails.put("field", "credentials");
            
            if (ex.getMessage().contains("계정이 잠겨")) {
                errorCode = ResponseCode.ACCOUNT_LOCKED;
                errorDetails.put("reason", ResponseCode.ACCOUNT_LOCKED);
            } else if (ex.getMessage().contains("사용자계정") || ex.getMessage().contains("비밀번호")) {
                errorCode = ResponseCode.INVALID_CREDENTIALS;
                errorDetails.put("reason", ResponseCode.INVALID_CREDENTIALS);
            }
        } else {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = ResponseCode.AUTHENTICATION_FAILED;
            errorDetails.put("message", ex.getMessage());
            errorDetails.put("code", errorCode);
        }
        
        UnifiedApiResponse<Object> response = UnifiedApiResponse.<Object>builder()
                .success(false)
                .code(errorCode)
                .message(ex.getMessage())
                .error(errorDetails)
                .timestamp(java.time.LocalDateTime.now())
                .path(requestUrl)
                .build();
        
        return ResponseEntity.status(status).body(response);
    }

    /**
     * 공개 API(문의하기 등) 호출 빈도 제한 초과
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<UnifiedApiResponse<Object>> handleRateLimitExceeded(
            RateLimitExceededException ex,
            WebRequest request) {
        log.warn("레이트 리밋 초과: {}", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        long retryAfter = ex.getRetryAfterSeconds();
        if (retryAfter > 0) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        }
        UnifiedApiResponse<Object> response = UnifiedApiResponse.<Object>builder()
                .success(false)
                .code(ResponseCode.RATE_LIMIT_EXCEEDED)
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getDescription(false))
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(response);
    }
    
    /**
     * 접근 권한 예외 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        
        log.error("접근 권한 예외 발생: {}", ex.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(
            "ACCESS_DENIED",
            "해당 리소스에 접근할 권한이 없습니다.",
            null
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
    
    /**
     * 잘못된 자격 증명 예외 처리
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<UnifiedApiResponse<Object>> handleBadCredentialsException(
            BadCredentialsException ex, WebRequest request) {
        
        log.error("잘못된 자격 증명: {}", ex.getMessage());
        
        // 에러 상세 정보 생성
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("message", "사용자계정 또는 비밀번호가 올바르지 않습니다.");
        errorDetails.put("code", ResponseCode.INVALID_CREDENTIALS);
        
        UnifiedApiResponse<Object> response = UnifiedApiResponse.<Object>builder()
                .success(false)
                .code(ResponseCode.INVALID_CREDENTIALS)
                .message("사용자계정 또는 비밀번호가 올바르지 않습니다.")
                .error(errorDetails)
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getDescription(false))
                .build();
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    /**
     * 사용자 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserNotFoundException(
            UserNotFoundException ex, WebRequest request) {
        
        log.error("사용자 찾을 수 없음: {}", ex.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(
            "USER_NOT_FOUND",
            ex.getMessage(),
            null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 투자 기록 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(InvestmentNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvestmentNotFoundException(
            InvestmentNotFoundException ex, WebRequest request) {
        
        log.error("투자 기록 찾을 수 없음: {}", ex.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(
            "INVESTMENT_NOT_FOUND",
            ex.getMessage(),
            null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 포트폴리오 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(PortfolioNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handlePortfolioNotFoundException(
            PortfolioNotFoundException ex, WebRequest request) {
        
        log.error("포트폴리오 찾을 수 없음: {}", ex.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(
            "PORTFOLIO_NOT_FOUND",
            ex.getMessage(),
            null
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 입력값 검증 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<UnifiedApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        log.error("입력값 검증 실패: {}", ex.getMessage());
        log.error("요청 URL: {}", request.getDescription(false));
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            String rejectedValue = ((FieldError) error).getRejectedValue() != null ? 
                ((FieldError) error).getRejectedValue().toString() : "null";
            
            log.error("필드 검증 실패 - 필드: {}, 값: {}, 오류: {}", fieldName, rejectedValue, errorMessage);
            errors.put(fieldName, errorMessage);
        });
        
        UnifiedApiResponse<Object> response = UnifiedApiResponse.<Object>builder()
                .success(false)
                .code(ResponseCode.VALIDATION_FAILED)
                .message("입력값이 올바르지 않습니다.")
                .error(errors)
                .timestamp(java.time.LocalDateTime.now())
                .path(request.getDescription(false))
                .build();
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.error("잘못된 인수: {}", ex.getMessage());
        
        ApiResponse<Object> response = ApiResponse.error(
            "INVALID_ARGUMENT",
            ex.getMessage(),
            null
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 일반적인 RuntimeException 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        
        log.error("런타임 예외 발생: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.error(
            "INTERNAL_ERROR",
            "내부 서버 오류가 발생했습니다.",
            null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * 기타 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("예상치 못한 예외 발생: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.error(
            "UNEXPECTED_ERROR",
            "예상치 못한 오류가 발생했습니다.",
            null
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
} 