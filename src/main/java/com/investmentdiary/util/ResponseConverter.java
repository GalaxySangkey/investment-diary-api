package com.investmentdiary.util;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.constants.ResponseCode;
import java.time.LocalDateTime;

/**
 * ApiResponse를 UnifiedApiResponse로 변환하는 유틸리티 클래스
 */
public class ResponseConverter {
    
    /**
     * ApiResponse를 UnifiedApiResponse로 변환
     */
    public static <T> UnifiedApiResponse<T> convert(ApiResponse<T> apiResponse, String path) {
        return UnifiedApiResponse.<T>builder()
                .success(apiResponse.isSuccess())
                .code(apiResponse.isSuccess() ? ResponseCode.SUCCESS : ResponseCode.INTERNAL_SERVER_ERROR)
                .message(apiResponse.getMessage())
                .data(apiResponse.getData())
                .count(calculateCount(apiResponse.getData()))
                .error(apiResponse.getError())
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    /**
     * 성공 응답을 UnifiedApiResponse로 변환
     */
    public static <T> UnifiedApiResponse<T> convertSuccess(ApiResponse<T> apiResponse, String path) {
        return UnifiedApiResponse.<T>builder()
                .success(true)
                .code(ResponseCode.SUCCESS)
                .message(apiResponse.getMessage())
                .data(apiResponse.getData())
                .count(calculateCount(apiResponse.getData()))
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    /**
     * 실패 응답을 UnifiedApiResponse로 변환
     */
    public static <T> UnifiedApiResponse<T> convertError(ApiResponse<T> apiResponse, String errorCode, String path) {
        return UnifiedApiResponse.<T>builder()
                .success(false)
                .code(errorCode)
                .message(apiResponse.getMessage())
                .data(apiResponse.getData())
                .error(apiResponse.getError())
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();
    }
    
    /**
     * 데이터 개수 계산
     */
    private static <T> Integer calculateCount(T data) {
        if (data == null) {
            return 0;
        }
        
        if (data instanceof java.util.Collection) {
            return ((java.util.Collection<?>) data).size();
        }
        
        if (data instanceof org.springframework.data.domain.Page) {
            return (int) ((org.springframework.data.domain.Page<?>) data).getTotalElements();
        }
        
        // 단일 객체인 경우
        return 1;
    }
}
