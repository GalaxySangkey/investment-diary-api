package com.investmentdiary.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentdiary.dto.queue.QueueStatusResponse;
import com.investmentdiary.dto.queue.QueueSubmitRequest;
import com.investmentdiary.dto.queue.QueueSubmitResponse;
import com.investmentdiary.entity.ApiRequestQueue;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.repository.ApiRequestQueueRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
@Transactional(readOnly = true)
public class QueueService {
    
    private static final Logger log = LoggerFactory.getLogger(QueueService.class);
    
    private final ApiRequestQueueRepository queueRepository;
    private final MessageQueueProducer messageQueueProducer;
    private final ObjectMapper objectMapper;
    
    @Value("${queue.max-retries:3}")
    private Integer defaultMaxRetries;
    
    public QueueService(ApiRequestQueueRepository queueRepository,
                       MessageQueueProducer messageQueueProducer) {
        this.queueRepository = queueRepository;
        this.messageQueueProducer = messageQueueProducer;
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }
    
    /**
     * API 요청을 큐에 제출
     */
    @Transactional
    public QueueSubmitResponse submitRequest(QueueSubmitRequest request, Long userId, HttpServletRequest httpRequest) {
        log.info("API 요청 큐 제출: endpoint={}, method={}, userId={}", request.getEndpoint(), request.getMethod(), userId);
        
        // Request ID 생성
        String requestId = UUID.randomUUID().toString();
        
        // 요청 본문 및 헤더를 JSON 문자열로 변환
        String requestBodyJson = null;
        String requestHeadersJson = null;
        
        try {
            requestBodyJson = objectMapper.writeValueAsString(request.getBody());
            if (request.getHeaders() != null) {
                requestHeadersJson = objectMapper.writeValueAsString(request.getHeaders());
            }
        } catch (Exception e) {
            log.error("요청 본문 변환 실패: {}", e.getMessage(), e);
            throw new RuntimeException("요청 본문 변환 실패: " + e.getMessage());
        }
        
        // 큐 엔티티 생성
        ApiRequestQueue queueItem = ApiRequestQueue.builder()
            .requestId(requestId)
            .userId(userId)
            .endpoint(request.getEndpoint())
            .method(request.getMethod())
            .requestBody(requestBodyJson)
            .requestHeaders(requestHeadersJson)
            .status(ApiRequestQueue.RequestStatus.PENDING)
            .retryCount(0)
            .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : defaultMaxRetries)
            .build();
        
        // 데이터베이스에 저장
        queueItem = queueRepository.save(queueItem);
        
        // 메시지 큐에 전송 (선택적)
        // 중요: DB에 저장되어 있으므로 메시지 큐 전송 실패해도 안전
        // QueueConsumer가 DB에서 PENDING 상태를 주기적으로 확인하여 처리
        try {
            messageQueueProducer.sendMessage(queueItem);
            log.info("메시지 큐에 전송 완료: requestId={}", requestId);
        } catch (Exception e) {
            log.warn("메시지 큐 전송 실패 (DB에 저장되어 있으므로 안전): requestId={}, error={}", requestId, e.getMessage());
            // 메시지 큐 전송 실패해도 DB에는 저장되어 있으므로 QueueConsumer가 주기적으로 확인하여 처리
            // 서버 재시작 시에도 DB에서 자동 복구됨
        }
        
        // 응답 생성
        return QueueSubmitResponse.builder()
            .requestId(requestId)
            .status("PENDING")
            .message("요청이 큐에 추가되었습니다.")
            .endpoint(request.getEndpoint())
            .build();
    }
    
    /**
     * 요청 상태 조회
     */
    public QueueStatusResponse getRequestStatus(String requestId) {
        ApiRequestQueue queueItem = queueRepository.findByRequestId(requestId)
            .orElseThrow(() -> new UserNotFoundException("요청을 찾을 수 없습니다: " + requestId));
        
        Object result = null;
        if (queueItem.getResultBody() != null) {
            try {
                result = objectMapper.readValue(queueItem.getResultBody(), Object.class);
            } catch (Exception e) {
                log.warn("결과 본문 파싱 실패: requestId={}", requestId);
            }
        }
        
        return QueueStatusResponse.builder()
            .requestId(queueItem.getRequestId())
            .status(queueItem.getStatus().name())
            .retryCount(queueItem.getRetryCount())
            .maxRetries(queueItem.getMaxRetries())
            .result(result)
            .errorMessage(queueItem.getErrorMessage())
            .createdAt(queueItem.getCreatedAt())
            .processedAt(queueItem.getProcessedAt())
            .build();
    }
    
    /**
     * 재시도 가능한 요청 조회
     */
    public java.util.List<ApiRequestQueue> getRetryableRequests() {
        return queueRepository.findRetryableRequests();
    }
    
    /**
     * 타임아웃된 처리 중인 요청 조회
     */
    public java.util.List<ApiRequestQueue> getTimeoutProcessingRequests(int timeoutMinutes) {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return queueRepository.findTimeoutProcessingRequests(timeout);
    }
}

