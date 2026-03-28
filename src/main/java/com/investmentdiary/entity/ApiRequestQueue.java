package com.investmentdiary.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "api_request_queue", indexes = {
    @Index(name = "idx_request_id", columnList = "request_id", unique = true),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRequestQueue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId; // UUID
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;
    
    @Column(name = "method", nullable = false, length = 10)
    private String method; // POST, GET, PUT, DELETE
    
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody; // JSON 문자열
    
    @Column(name = "request_headers", columnDefinition = "TEXT")
    private String requestHeaders; // JSON 문자열
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 3;
    
    @Column(name = "result_body", columnDefinition = "TEXT")
    private String resultBody; // JSON 문자열
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    public enum RequestStatus {
        PENDING,      // 대기 중
        PROCESSING,   // 처리 중
        COMPLETED,    // 완료
        FAILED,       // 실패
        RETRYING      // 재시도 중
    }
    
    // 비즈니스 메서드
    public void incrementRetryCount() {
        this.retryCount++;
        if (this.retryCount >= this.maxRetries) {
            this.status = RequestStatus.FAILED;
        } else {
            this.status = RequestStatus.RETRYING;
        }
    }
    
    public void markAsProcessing() {
        this.status = RequestStatus.PROCESSING;
    }
    
    public void markAsCompleted(String resultBody) {
        this.status = RequestStatus.COMPLETED;
        this.resultBody = resultBody;
        this.processedAt = LocalDateTime.now();
    }
    
    public void markAsFailed(String errorMessage) {
        this.status = RequestStatus.FAILED;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }
    
    public boolean canRetry() {
        return this.retryCount < this.maxRetries && 
               (this.status == RequestStatus.FAILED || this.status == RequestStatus.RETRYING);
    }
}


