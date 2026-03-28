package com.investmentdiary.repository;

import com.investmentdiary.entity.ApiRequestQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiRequestQueueRepository extends JpaRepository<ApiRequestQueue, Long> {
    
    // Request ID로 조회
    Optional<ApiRequestQueue> findByRequestId(String requestId);
    
    // 상태별 조회
    List<ApiRequestQueue> findByStatus(ApiRequestQueue.RequestStatus status);
    Page<ApiRequestQueue> findByStatus(ApiRequestQueue.RequestStatus status, Pageable pageable);
    
    // 사용자별 조회
    List<ApiRequestQueue> findByUserId(Long userId);
    Page<ApiRequestQueue> findByUserId(Long userId, Pageable pageable);
    
    // 재시도 가능한 요청 조회
    @Query("SELECT a FROM ApiRequestQueue a WHERE a.status IN ('FAILED', 'RETRYING') AND a.retryCount < a.maxRetries")
    List<ApiRequestQueue> findRetryableRequests();
    
    // 특정 시간 이전의 대기 중인 요청 조회
    @Query("SELECT a FROM ApiRequestQueue a WHERE a.status = 'PENDING' AND a.createdAt < :before")
    List<ApiRequestQueue> findPendingRequestsBefore(@Param("before") LocalDateTime before);
    
    // 처리 중인 요청 조회 (타임아웃 확인용)
    @Query("SELECT a FROM ApiRequestQueue a WHERE a.status = 'PROCESSING' AND a.updatedAt < :timeout")
    List<ApiRequestQueue> findTimeoutProcessingRequests(@Param("timeout") LocalDateTime timeout);
    
    // 통계
    long countByStatus(ApiRequestQueue.RequestStatus status);
    long countByUserId(Long userId);
}


