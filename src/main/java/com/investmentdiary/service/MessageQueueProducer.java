package com.investmentdiary.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentdiary.entity.ApiRequestQueue;

/**
 * 메시지 큐 Producer
 * RabbitMQ, Redis, Memory 중 선택하여 사용
 * 
 * 중요: 메시지 큐는 성능 향상을 위한 선택적 기능입니다.
 * 모든 요청은 DB에 저장되므로 메시지 큐가 소실되어도 안전합니다.
 * - DB 저장: 영구 저장 (서버 재시작 시에도 유지)
 * - 메시지 큐: 빠른 처리 (서버 재시작 시 소실 가능, Memory 큐의 경우)
 * - QueueConsumer: DB에서 PENDING 요청을 주기적으로 확인하여 처리
 */
@Service
public class MessageQueueProducer {
    
    private static final Logger log = LoggerFactory.getLogger(MessageQueueProducer.class);
    
    @Value("${queue.type:memory}")
    private String queueType;
    
    @Value("${queue.name:api_requests}")
    private String queueName;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // RabbitMQ Template (선택적)
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    
    // Redis Template (선택적)
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    
    // Memory Queue (fallback)
    private final java.util.concurrent.BlockingQueue<String> memoryQueue = new java.util.concurrent.LinkedBlockingQueue<>();
    
    @Autowired(required = false)
    public void setRabbitTemplate(org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    @Autowired(required = false)
    public void setRedisTemplate(org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 메시지를 큐에 전송
     */
    public void sendMessage(ApiRequestQueue queueItem) {
        try {
            String message = objectMapper.writeValueAsString(queueItem.getRequestId());
            
            if ("rabbitmq".equals(queueType) && rabbitTemplate != null) {
                // RabbitMQ 사용
                rabbitTemplate.convertAndSend(queueName, message);
                log.debug("RabbitMQ에 메시지 전송: requestId={}", queueItem.getRequestId());
            } else if ("redis".equals(queueType) && redisTemplate != null) {
                // Redis 사용
                redisTemplate.opsForList().rightPush(queueName, message);
                log.debug("Redis에 메시지 전송: requestId={}", queueItem.getRequestId());
            } else {
                // Memory Queue 사용 (fallback)
                // 주의: Memory Queue는 서버 재시작 시 소실되지만, DB에 저장되어 있으므로 안전
                // QueueConsumer가 DB에서 PENDING 요청을 주기적으로 확인하여 처리
                memoryQueue.offer(message);
                log.debug("Memory Queue에 메시지 전송 (서버 재시작 시 소실 가능, DB에 저장되어 있으므로 안전): requestId={}", 
                    queueItem.getRequestId());
            }
        } catch (Exception e) {
            log.error("메시지 큐 전송 실패: requestId={}, error={}", queueItem.getRequestId(), e.getMessage(), e);
            throw new RuntimeException("메시지 큐 전송 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * Memory Queue에서 메시지 수신 (Consumer에서 사용)
     */
    public String receiveFromMemoryQueue() {
        return memoryQueue.poll();
    }
    
    /**
     * 현재 사용 중인 큐 타입 반환
     */
    public String getQueueType() {
        if ("rabbitmq".equals(queueType) && rabbitTemplate != null) {
            return "rabbitmq";
        } else if ("redis".equals(queueType) && redisTemplate != null) {
            return "redis";
        } else {
            return "memory";
        }
    }
}

