package com.investmentdiary.dto.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusResponse {
    
    private String requestId;
    private String status;
    private Integer retryCount;
    private Integer maxRetries;
    private Object result;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}


