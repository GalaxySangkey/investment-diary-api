package com.investmentdiary.dto.queue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueSubmitRequest {
    
    @NotBlank(message = "endpoint는 필수입니다.")
    private String endpoint;
    
    @NotBlank(message = "method는 필수입니다.")
    private String method; // POST, GET, PUT, DELETE
    
    @NotNull(message = "body는 필수입니다.")
    private Object body; // 요청 본문
    
    private Map<String, String> headers; // 요청 헤더 (선택)
    
    private Integer maxRetries; // 최대 재시도 횟수 (기본값: 3)
}


