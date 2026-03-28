package com.investmentdiary.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.dto.queue.QueueStatusResponse;
import com.investmentdiary.dto.queue.QueueSubmitRequest;
import com.investmentdiary.dto.queue.QueueSubmitResponse;
import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.service.QueueService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/queue")
@Tag(name = "메시지 큐", description = "API 요청 큐 처리 관련 API")
public class QueueController {
    
    private static final Logger log = LoggerFactory.getLogger(QueueController.class);
    
    private final QueueService queueService;
    
    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }
    
    @PostMapping("/submit")
    @Operation(summary = "API 요청 큐 제출", description = "API 요청을 큐에 제출하여 비동기로 처리합니다.")
    public UnifiedApiResponse<QueueSubmitResponse> submitRequest(
            @Valid @RequestBody QueueSubmitRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("큐 제출 요청: endpoint={}, method={}", request.getEndpoint(), request.getMethod());
            
            // TODO: 실제 사용자 ID는 Security Context에서 가져와야 함
            Long userId = null; // 임시로 null
            
            QueueSubmitResponse response = queueService.submitRequest(request, userId, httpRequest);
            
            UnifiedApiResponse<QueueSubmitResponse> apiResponse = UnifiedApiResponse.<QueueSubmitResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("요청이 큐에 추가되었습니다.")
                    .data(response)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            log.info("큐 제출 성공: requestId={}", response.getRequestId());
            
            return apiResponse;
        } catch (Exception e) {
            log.error("큐 제출 실패: error={}", e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<QueueSubmitResponse> apiResponse = UnifiedApiResponse.<QueueSubmitResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("큐 제출 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }
    
    @GetMapping("/status/{requestId}")
    @Operation(summary = "요청 상태 조회", description = "요청 ID로 처리 상태를 조회합니다.")
    public UnifiedApiResponse<QueueStatusResponse> getRequestStatus(
            @PathVariable String requestId,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("요청 상태 조회: requestId={}", requestId);
            
            QueueStatusResponse response = queueService.getRequestStatus(requestId);
            
            UnifiedApiResponse<QueueStatusResponse> apiResponse = UnifiedApiResponse.<QueueStatusResponse>builder()
                    .success(true)
                    .code(ResponseCode.SUCCESS)
                    .message("요청 상태 조회 성공")
                    .data(response)
                    .count(1)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        } catch (Exception e) {
            log.error("요청 상태 조회 실패: requestId={}, error={}", requestId, e.getMessage(), e);
            
            java.util.Map<String, Object> errorDetails = new java.util.HashMap<>();
            errorDetails.put("type", e.getClass().getSimpleName());
            errorDetails.put("message", e.getMessage());
            errorDetails.put("code", ResponseCode.INTERNAL_SERVER_ERROR);
            
            UnifiedApiResponse<QueueStatusResponse> apiResponse = UnifiedApiResponse.<QueueStatusResponse>builder()
                    .success(false)
                    .code(ResponseCode.INTERNAL_SERVER_ERROR)
                    .message("요청 상태 조회 중 오류가 발생했습니다: " + e.getMessage())
                    .error(errorDetails)
                    .timestamp(java.time.LocalDateTime.now())
                    .path(httpRequest.getRequestURI())
                    .build();
            
            return apiResponse;
        }
    }
}


