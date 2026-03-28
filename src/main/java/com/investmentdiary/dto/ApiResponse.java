package com.investmentdiary.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private boolean success;
    private T data;
    private String message;
    private ErrorInfo error;
    private LocalDateTime timestamp;
    
    // 명시적인 생성자 추가
    public ApiResponse(boolean success, T data, String message, ErrorInfo error, LocalDateTime timestamp) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
        this.timestamp = timestamp;
    }
    
    // 명시적인 getter 메서드 추가
    public T getData() {
        return this.data;
    }
    
    public boolean isSuccess() {
        return this.success;
    }
    
    public String getMessage() {
        return this.message;
    }
    
    public ErrorInfo getError() {
        return this.error;
    }
    
    public LocalDateTime getTimestamp() {
        return this.timestamp;
    }
    
    // 명시적인 builder() 메서드 추가
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }
    
    // 명시적인 Builder 클래스
    public static class Builder<T> {
        private boolean success;
        private T data;
        private String message;
        private ErrorInfo error;
        private LocalDateTime timestamp;
        
        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }
        
        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder<T> error(ErrorInfo error) {
            this.error = error;
            return this;
        }
        
        public Builder<T> timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public ApiResponse<T> build() {
            return new ApiResponse<T>(success, data, message, error, timestamp);
        }
    }
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static <T> ApiResponse<T> error(String code, String message, Object details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private String code;
        private String message;
        private Object details;
        
        // 명시적인 생성자 추가
        public ErrorInfo(String code, String message, Object details) {
            this.code = code;
            this.message = message;
            this.details = details;
        }
        
        // 명시적인 builder() 메서드 추가
        public static Builder builder() {
            return new Builder();
        }
        
        // 명시적인 Builder 클래스
        public static class Builder {
            private String code;
            private String message;
            private Object details;
            
            public Builder code(String code) {
                this.code = code;
                return this;
            }
            
            public Builder message(String message) {
                this.message = message;
                return this;
            }
            
            public Builder details(Object details) {
                this.details = details;
                return this;
            }
            
            public ErrorInfo build() {
                return new ErrorInfo(code, message, details);
            }
        }
    }
} 