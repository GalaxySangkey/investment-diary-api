package com.investmentdiary.repository;

import com.investmentdiary.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    // 기본 조회 메서드
    Optional<UserSession> findBySessionId(String sessionId);
    Optional<UserSession> findByAccessToken(String accessToken);
    
    // 사용자별 세션 조회
    List<UserSession> findByUserId(Long userId);
    Optional<UserSession> findByUserIdAndIsActiveTrue(Long userId);
    Optional<UserSession> findByUserIdAndAccessTokenAndIsActiveTrue(Long userId, String accessToken);
    
    // 활성 세션 조회
    List<UserSession> findByIsActiveTrue();
    
    // 만료된 세션 조회
    @Query("SELECT us FROM UserSession us WHERE us.expiresAt < :now")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);
    
    // 장기간 비활성 세션 조회
    @Query("SELECT us FROM UserSession us WHERE us.lastActivityAt < :threshold AND us.isActive = true")
    List<UserSession> findInactiveSessions(@Param("threshold") LocalDateTime threshold);
    
    // 특정 기간 세션 조회
    List<UserSession> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    // 세션 통계
    long countByUserIdAndIsActiveTrue(Long userId);
    long countByIsActiveTrue();
    
    // 세션 정리
    @Query("DELETE FROM UserSession us WHERE us.expiresAt < :now OR us.isActive = false")
    void deleteExpiredAndInactiveSessions(@Param("now") LocalDateTime now);
} 