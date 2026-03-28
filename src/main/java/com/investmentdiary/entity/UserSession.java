package com.investmentdiary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_access_token", columnList = "access_token"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    private String sessionId;
    
    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;
    
    @Column(name = "device_info", length = 200)
    private String deviceInfo;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;
    
    @Column(name = "logged_out_at")
    private LocalDateTime loggedOutAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 비즈니스 메서드
    public void updateLastActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }
    
    public void logout() {
        this.isActive = false;
        this.loggedOutAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return this.expiresAt != null && LocalDateTime.now().isAfter(this.expiresAt);
    }
    
    public boolean isValid() {
        return this.isActive && !this.isExpired();
    }
    
    // 추가 setter 메서드들 (Lombok @Setter가 있지만 명시적으로 추가)
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
    
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    public void setLoggedOutAt(LocalDateTime loggedOutAt) {
        this.loggedOutAt = loggedOutAt;
    }
    
    // 명시적인 builder() 메서드 추가
    public static Builder builder() {
        return new Builder();
    }
    
    // 명시적인 Builder 클래스
    public static class Builder {
        private Long id;
        private User user;
        private String sessionId;
        private String accessToken;
        private String deviceInfo;
        private String ipAddress;
        private String userAgent;
        private Boolean isActive = true;
        private LocalDateTime lastActivityAt;
        private LocalDateTime loggedOutAt;
        private LocalDateTime expiresAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public Builder id(Long id) { this.id = id; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder accessToken(String accessToken) { this.accessToken = accessToken; return this; }
        public Builder deviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder isActive(Boolean isActive) { this.isActive = isActive; return this; }
        public Builder lastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; return this; }
        public Builder loggedOutAt(LocalDateTime loggedOutAt) { this.loggedOutAt = loggedOutAt; return this; }
        public Builder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        
        public UserSession build() {
            UserSession session = new UserSession();
            session.id = this.id;
            session.user = this.user;
            session.sessionId = this.sessionId;
            session.accessToken = this.accessToken;
            session.deviceInfo = this.deviceInfo;
            session.ipAddress = this.ipAddress;
            session.userAgent = this.userAgent;
            session.isActive = this.isActive;
            session.lastActivityAt = this.lastActivityAt;
            session.loggedOutAt = this.loggedOutAt;
            session.expiresAt = this.expiresAt;
            session.createdAt = this.createdAt;
            session.updatedAt = this.updatedAt;
            return session;
        }
    }
} 