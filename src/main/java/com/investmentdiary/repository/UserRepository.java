package com.investmentdiary.repository;

import com.investmentdiary.entity.User;
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
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 기본 조회 메서드
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameAndStatus(String username, User.UserStatus status);
    
    // 존재 여부 확인
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    // 상태별 사용자 조회
    List<User> findByStatus(User.UserStatus status);
    Page<User> findByStatus(User.UserStatus status, Pageable pageable);
    
    // 역할별 사용자 조회
    List<User> findByRole(User.UserRole role);
    Page<User> findByRole(User.UserRole role, Pageable pageable);
    
    // 로그인 관련 조회
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.status = 'ACTIVE' AND (u.lockedUntil IS NULL OR u.lockedUntil < :now)")
    Optional<User> findActiveUserByUsername(@Param("username") String username, @Param("now") LocalDateTime now);
    
    // 계정 잠금 해제가 필요한 사용자 조회
    @Query("SELECT u FROM User u WHERE u.status = 'LOCKED' AND u.lockedUntil < :now")
    List<User> findUsersToUnlock(@Param("now") LocalDateTime now);
    
    // 최근 로그인 사용자 조회
    @Query("SELECT u FROM User u WHERE u.lastLoginAt >= :since ORDER BY u.lastLoginAt DESC")
    List<User> findRecentlyActiveUsers(@Param("since") LocalDateTime since);
    
    // 이메일 인증이 필요한 사용자 조회
    List<User> findByEmailVerifiedFalse();
    
    // 전화번호 인증이 필요한 사용자 조회
    List<User> findByPhoneVerifiedFalse();
    
    // 검색 기능
    @Query("SELECT u FROM User u WHERE " +
           "(:username IS NULL OR u.username LIKE %:username%) AND " +
           "(:email IS NULL OR u.email LIKE %:email%) AND " +
           "(:nickname IS NULL OR u.nickname LIKE %:nickname%) AND " +
           "(:role IS NULL OR u.role = :role) AND " +
           "(:status IS NULL OR u.status = :status)")
    Page<User> searchUsers(
        @Param("username") String username,
        @Param("email") String email,
        @Param("nickname") String nickname,
        @Param("role") User.UserRole role,
        @Param("status") User.UserStatus status,
        Pageable pageable
    );
    
    // 통계 조회
    @Query("SELECT COUNT(u) FROM User u WHERE u.status = 'ACTIVE'")
    long countActiveUsers();
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since")
    long countNewUsersSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
    List<Object[]> countUsersByRole();
    
    // 성능 최적화를 위한 인덱스 힌트
    @Query(value = "SELECT * FROM users u USE INDEX (idx_username) WHERE u.username = :username", nativeQuery = true)
    Optional<User> findByUsernameWithIndex(@Param("username") String username);
} 