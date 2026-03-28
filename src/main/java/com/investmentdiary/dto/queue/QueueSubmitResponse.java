package com.investmentdiary.dto.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueSubmitResponse {
    
    private String requestId;
    private String status;
    private String message;
    private String endpoint;
}


