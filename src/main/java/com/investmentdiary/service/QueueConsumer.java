package com.investmentdiary.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentdiary.entity.ApiRequestQueue;
import com.investmentdiary.repository.ApiRequestQueueRepository;

import jakarta.annotation.PostConstruct;

/**
 * 메시지 큐 Consumer
 * 백그라운드에서 큐의 메시지를 소비하여 처리
 */
@Service
public class QueueConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(QueueConsumer.class);
    
    private final ApiRequestQueueRepository queueRepository;
    private final MessageQueueProducer messageQueueProducer;
    private final QueueProcessor queueProcessor;
    
    @Value("${queue.retry-delay:5000}")
    private Long retryDelayMs;
    
    @Value("${queue.processing-timeout:30}")
    private Integer processingTimeoutMinutes;
    
    private boolean isRunning = false;
    
    public QueueConsumer(ApiRequestQueueRepository queueRepository,
                        MessageQueueProducer messageQueueProducer,
                        QueueProcessor queueProcessor) {
        this.queueRepository = queueRepository;
        this.messageQueueProducer = messageQueueProducer;
        this.queueProcessor = queueProcessor;
    }
    
    @PostConstruct
    public void init() {
        log.info("QueueConsumer 초기화: queueType={}", messageQueueProducer.getQueueType());
        
        // 서버 시작 시 DB에 저장된 PENDING 요청 복구
        recoverPendingRequests();
    }
    
    /**
     * 서버 재시작 시 DB에 저장된 PENDING 요청 복구
     * 메시지 큐가 소실되어도 DB에서 복구 가능
     */
    @Transactional
    public void recoverPendingRequests() {
        try {
            List<ApiRequestQueue> pendingRequests = queueRepository.findByStatus(ApiRequestQueue.RequestStatus.PENDING);
            
            if (!pendingRequests.isEmpty()) {
                log.info("서버 재시작: DB에서 {}개의 PENDING 요청 복구", pendingRequests.size());
                
                // 각 요청을 메시지 큐에 다시 전송 (선택적)
                // DB에서 직접 처리하므로 메시지 큐 재전송은 선택사항
                for (ApiRequestQueue request : pendingRequests) {
                    try {
                        messageQueueProducer.sendMessage(request);
                        log.debug("복구된 요청을 메시지 큐에 재전송: requestId={}", request.getRequestId());
                    } catch (Exception e) {
                        log.warn("복구된 요청 메시지 큐 재전송 실패 (DB에서 직접 처리): requestId={}, error={}", 
                            request.getRequestId(), e.getMessage());
                        // 메시지 큐 재전송 실패해도 DB에 있으므로 consumeMessages에서 처리됨
                    }
                }
            } else {
                log.info("서버 재시작: 복구할 PENDING 요청 없음");
            }
            
            // 타임아웃된 PROCESSING 요청도 복구
            List<ApiRequestQueue> timeoutRequests = queueRepository.findTimeoutProcessingRequests(
                LocalDateTime.now().minusMinutes(processingTimeoutMinutes));
            
            if (!timeoutRequests.isEmpty()) {
                log.warn("서버 재시작: {}개의 타임아웃된 PROCESSING 요청 발견, PENDING으로 복구", timeoutRequests.size());
                for (ApiRequestQueue request : timeoutRequests) {
                    request.setStatus(ApiRequestQueue.RequestStatus.PENDING);
                    queueRepository.save(request);
                }
            }
            
        } catch (Exception e) {
            log.error("PENDING 요청 복구 중 오류: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 큐에서 메시지를 소비하여 처리 (5초마다 실행)
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void consumeMessages() {
        if (isRunning) {
            return; // 이미 실행 중이면 건너뜀
        }
        
        try {
            isRunning = true;
            
            // 대기 중인 요청 처리
            List<ApiRequestQueue> pendingRequests = queueRepository.findByStatus(ApiRequestQueue.RequestStatus.PENDING);
            for (ApiRequestQueue request : pendingRequests) {
                processRequest(request);
            }
            
            // 타임아웃된 처리 중인 요청 재시도
            List<ApiRequestQueue> timeoutRequests = queueRepository.findTimeoutProcessingRequests(
                LocalDateTime.now().minusMinutes(processingTimeoutMinutes));
            for (ApiRequestQueue request : timeoutRequests) {
                log.warn("타임아웃된 요청 발견: requestId={}, 재시도", request.getRequestId());
                request.incrementRetryCount();
                if (request.canRetry()) {
                    request.setStatus(ApiRequestQueue.RequestStatus.PENDING);
                    queueRepository.save(request);
                } else {
                    request.markAsFailed("처리 타임아웃");
                    queueRepository.save(request);
                }
            }
            
        } catch (Exception e) {
            log.error("메시지 소비 중 오류: {}", e.getMessage(), e);
        } finally {
            isRunning = false;
        }
    }
    
    /**
     * 재시도 가능한 요청 처리 (1분마다 실행)
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void retryFailedRequests() {
        List<ApiRequestQueue> retryableRequests = queueRepository.findRetryableRequests();
        
        for (ApiRequestQueue request : retryableRequests) {
            // 재시도 지연 시간 확인
            long delayMs = retryDelayMs * (request.getRetryCount() + 1);
            long elapsedMs = java.time.Duration.between(request.getUpdatedAt(), LocalDateTime.now()).toMillis();
            
            if (elapsedMs >= delayMs) {
                log.info("재시도 요청 처리: requestId={}, retryCount={}", request.getRequestId(), request.getRetryCount());
                request.setStatus(ApiRequestQueue.RequestStatus.PENDING);
                queueRepository.save(request);
            }
        }
    }
    
    /**
     * 개별 요청 처리
     */
    private void processRequest(ApiRequestQueue request) {
        try {
            log.info("요청 처리 시작: requestId={}, endpoint={}", request.getRequestId(), request.getEndpoint());
            
            // 처리 중 상태로 변경
            request.markAsProcessing();
            queueRepository.save(request);
            
            // 실제 비즈니스 로직 처리
            String result = queueProcessor.processRequest(request);
            
            // 완료 처리
            request.markAsCompleted(result);
            queueRepository.save(request);
            
            log.info("요청 처리 완료: requestId={}", request.getRequestId());
            
        } catch (Exception e) {
            log.error("요청 처리 실패: requestId={}, error={}", request.getRequestId(), e.getMessage(), e);
            
            // 재시도 가능 여부 확인
            request.incrementRetryCount();
            if (request.canRetry()) {
                request.setStatus(ApiRequestQueue.RequestStatus.RETRYING);
                log.info("재시도 예약: requestId={}, retryCount={}", request.getRequestId(), request.getRetryCount());
            } else {
                request.markAsFailed(e.getMessage());
                log.error("최대 재시도 횟수 초과: requestId={}", request.getRequestId());
            }
            queueRepository.save(request);
        }
    }
}

