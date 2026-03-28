package com.investmentdiary.dto;

import java.time.LocalDateTime;
import java.util.Collection;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.investmentdiary.constants.ResponseCode;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 통일된 API 응답 구조
 * 성공과 실패 모두 동일한 구조를 사용하여 프론트엔드 처리 일관성 확보
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedApiResponse<T> {
    
    /**
     * 요청 성공 여부
     */
    private boolean success;
    
    /**
     * 응답 코드 (텍스트)
     */
    private String code;
    
    /**
     * 응답 코드 (숫자)
     */
    private Integer codeNumber;
    
    /**
     * 응답 메시지 (성공/실패 모두 동일한 필드 사용)
     */
    private String message;
    
    /**
     * 응답 데이터 (성공 시에만 포함)
     */
    private T data;
    
    /**
     * 데이터 개수 (리스트 응답 시 유용)
     */
    private Integer count;
    
    /**
     * 에러 상세 정보 (실패 시에만 포함)
     */
    private Object error;
    
    /**
     * 응답 생성 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 요청 경로 (디버깅용)
     */
    private String path;
    
    // 명시적인 생성자
    public UnifiedApiResponse(boolean success, String code, Integer codeNumber, String message, T data, Integer count, Object error, LocalDateTime timestamp, String path) {
        this.success = success;
        this.code = code;
        this.codeNumber = codeNumber;
        this.message = message;
        this.data = data;
        this.count = count;
        this.error = error;
        this.timestamp = timestamp;
        this.path = path;
    }
    
    // 명시적인 getter 메서드들
    public boolean isSuccess() { return this.success; }
    public String getCode() { return this.code; }
    public Integer getCodeNumber() { return this.codeNumber; }
    public String getMessage() { return this.message; }
    public T getData() { return this.data; }
    public Integer getCount() { return this.count; }
    public Object getError() { return this.error; }
    public LocalDateTime getTimestamp() { return this.timestamp; }
    public String getPath() { return this.path; }
    
    // Builder 패턴
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    public static class Builder<T> {
        private boolean success;
        private String code;
        private Integer codeNumber;
        private String message;
        private T data;
        private Integer count;
        private Object error;
        private LocalDateTime timestamp;
        private String path;
        
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<T> code(String code) {
            this.code = code;
            this.codeNumber = ResponseCode.getNumericCode(code);
            return this;
        }
        
        public Builder<T> codeNumber(Integer codeNumber) {
            this.codeNumber = codeNumber;
            this.code = ResponseCode.getTextCode(codeNumber);
            return this;
        }
        
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder<T> data(T data) {
            this.data = data;
            // 데이터가 Collection인 경우 자동으로 count 설정
            if (data instanceof Collection) {
                this.count = ((Collection<?>) data).size();
            }
            return this;
        }
        
        public Builder<T> count(Integer count) {
            this.count = count;
            return this;
        }
        
        public Builder<T> error(Object error) {
            this.error = error;
            return this;
        }
        
        public Builder<T> timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder<T> path(String path) {
            this.path = path;
            return this;
        }
        
        public UnifiedApiResponse<T> build() {
            return new UnifiedApiResponse<>(success, code, codeNumber, message, data, count, error, timestamp, path);
        }
    }
    
    // 성공 응답 생성 메서드들
    public static <T> UnifiedApiResponse<T> success(T data) {
        return UnifiedApiResponse.<T>builder()
                .success(true)
                .code(ResponseCode.SUCCESS)
                .message("요청이 성공적으로 처리되었습니다.")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> UnifiedApiResponse<T> success(T data, String message) {
        return UnifiedApiResponse.<T>builder()
                .success(true)
                .code(ResponseCode.SUCCESS)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> UnifiedApiResponse<T> success(String code, String message, T data) {
        return UnifiedApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> UnifiedApiResponse<T> success(String code, String message, T data, Integer count) {
        return UnifiedApiResponse.<T>builder()
                .success(true)
                .code(code)
                .message(message)
                .data(data)
                .count(count)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 실패 응답 생성 메서드들
    public static <T> UnifiedApiResponse<T> error(String code, String message) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> UnifiedApiResponse<T> error(String code, String message, Object errorDetails) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .error(errorDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> UnifiedApiResponse<T> error(String code, String message, Object errorDetails, String path) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .error(errorDetails)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    // 유효성 검사 실패 응답
    public static <T> UnifiedApiResponse<T> validationError(String message, Object validationErrors) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.VALIDATION_FAILED)
                .message(message)
                .error(validationErrors)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 인증 실패 응답
    public static <T> UnifiedApiResponse<T> authenticationError(String message) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.AUTHENTICATION_FAILED)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 권한 없음 응답
    public static <T> UnifiedApiResponse<T> authorizationError(String message) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.AUTHORIZATION_FAILED)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 서버 오류 응답
    public static <T> UnifiedApiResponse<T> serverError(String message) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.INTERNAL_SERVER_ERROR)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 회원가입 실패 응답
    public static <T> UnifiedApiResponse<T> registerError(String message, Object errorDetails) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.REGISTER_FAILED)
                .message(message)
                .error(errorDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 중복 사용자계정 에러 응답
    public static <T> UnifiedApiResponse<T> duplicateUsernameError(String message) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.DUPLICATE_USERNAME)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // 중복 이메일 에러 응답
    public static <T> UnifiedApiResponse<T> duplicateEmailError(String message) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(ResponseCode.DUPLICATE_EMAIL)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
