package com.investmentdiary.exception;

/**
 * 커스텀 인증 관련 예외
 */
public class CustomAuthenticationException extends RuntimeException {
    
    public CustomAuthenticationException(String message) {
        super(message);
    }
    
    public CustomAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
} 