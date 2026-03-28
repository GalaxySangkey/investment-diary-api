package com.investmentdiary.repository;

import com.investmentdiary.entity.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, Long> {
    
    // Credential ID로 조회
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    
    // 사용자별 Credential 조회
    List<WebAuthnCredential> findByUserId(Long userId);
    
    // 사용자별 활성 Credential 조회
    @Query("SELECT wc FROM WebAuthnCredential wc WHERE wc.user.id = :userId ORDER BY wc.lastUsedAt DESC NULLS LAST, wc.createdAt DESC")
    List<WebAuthnCredential> findByUserIdOrderByLastUsedAtDesc(@Param("userId") Long userId);
    
    // Credential ID와 User ID로 조회 (인증 시 사용)
    @Query("SELECT wc FROM WebAuthnCredential wc WHERE wc.credentialId = :credentialId AND wc.user.id = :userId")
    Optional<WebAuthnCredential> findByCredentialIdAndUserId(@Param("credentialId") String credentialId, @Param("userId") Long userId);
    
    // 사용자별 Credential 개수
    long countByUserId(Long userId);
    
    // Credential ID 존재 여부 확인
    boolean existsByCredentialId(String credentialId);
    
    // 모든 활성 사용자의 Credential 조회 (사용자명 없이 인증 시 사용)
    @Query("SELECT wc FROM WebAuthnCredential wc JOIN wc.user u WHERE u.status = 'ACTIVE' ORDER BY wc.lastUsedAt DESC NULLS LAST, wc.createdAt DESC")
    List<WebAuthnCredential> findAllByUserActiveTrueOrderByLastUsedAtDesc();
}


