package com.investmentdiary.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.investmentdiary.util.UserNameAttributeConverter;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_phone", columnList = "phone_encrypted")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50, unique = true)
    @NotBlank(message = "사용자계정은 필수입니다")
    @Size(min = 2, max = 50, message = "사용자계정은 2-50자 사이여야 합니다")
    private String username;
    
    @Column(nullable = true) // WebAuthn 사용으로 nullable로 변경
    private String password; // 레거시 지원을 위해 유지 (WebAuthn 전환 후 제거 가능)
    
    @Column(nullable = false, length = 100, unique = true)
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;
    
    @Column(nullable = false, length = 255)
    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 50, message = "이름은 2-50자 사이여야 합니다")
    @Convert(converter = UserNameAttributeConverter.class)
    private String name; // 실제 이름 (DB 저장 시 암호화)
    
    @Column(nullable = false, length = 50)
    @NotBlank(message = "닉네임은 필수입니다")
    @Size(min = 2, max = 50, message = "닉네임은 2-50자 사이여야 합니다")
    private String nickname;
    
    @Column(name = "phone_encrypted", length = 255)
    private String phoneEncrypted;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
    
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "login_attempts")
    private Integer loginAttempts = 0;
    
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;
    
    @Column(name = "email_verified")
    private Boolean emailVerified = false;
    
    @Column(name = "phone_verified")
    private Boolean phoneVerified = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "password_change_deferred_until")
    private LocalDateTime passwordChangeDeferredUntil;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 연관관계
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // 순환 참조 방지
    private List<InvestmentRecord> investmentRecords = new ArrayList<>();
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // 순환 참조 방지
    private PortfolioSettings portfolioSettings;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserSession> userSessions = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CommunityPost> communityPosts = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CommunityComment> communityComments = new ArrayList<>();
    
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private UserLevel userLevel;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PointHistory> pointHistory = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Notification> notifications = new ArrayList<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<WebAuthnCredential> webauthnCredentials = new ArrayList<>();
    
    // 비즈니스 메서드
    public void incrementLoginAttempts() {
        this.loginAttempts++;
        if (this.loginAttempts >= 5) {
            this.status = UserStatus.LOCKED;
            this.lockedUntil = LocalDateTime.now().plusHours(1);
        }
    }
    
    public void resetLoginAttempts() {
        this.loginAttempts = 0;
        this.status = UserStatus.ACTIVE;
        this.lockedUntil = null;
    }
    
    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
        this.resetLoginAttempts();
    }
    
    public boolean isLocked() {
        return this.status == UserStatus.LOCKED && 
               this.lockedUntil != null && 
               this.lockedUntil.isAfter(LocalDateTime.now());
    }
    
    public boolean isActive() {
        return this.status == UserStatus.ACTIVE && !this.isLocked();
    }
    
    // 연관관계 편의 메서드
    public void addInvestmentRecord(InvestmentRecord record) {
        this.investmentRecords.add(record);
        record.setUser(this);
    }
    
    public void removeInvestmentRecord(InvestmentRecord record) {
        this.investmentRecords.remove(record);
        record.setUser(null);
    }
    
    // 명시적인 getter 메서드들 (Lombok 대신)
    public Long getId() { return this.id; }
    public String getUsername() { return this.username; }
    public String getPassword() { return this.password; }
    public String getEmail() { return this.email; }
    public String getName() { return this.name; }
    public String getNickname() { return this.nickname; }
    public String getPhoneEncrypted() { return this.phoneEncrypted; }
    public UserRole getRole() { return this.role; }
    public UserStatus getStatus() { return this.status; }
    public LocalDateTime getLastLoginAt() { return this.lastLoginAt; }
    public Integer getLoginAttempts() { return this.loginAttempts; }
    public LocalDateTime getLockedUntil() { return this.lockedUntil; }
    public Boolean getEmailVerified() { return this.emailVerified; }
    public Boolean getPhoneVerified() { return this.phoneVerified; }
    public LocalDateTime getPasswordChangedAt() { return this.passwordChangedAt; }
    public LocalDateTime getPasswordChangeDeferredUntil() { return this.passwordChangeDeferredUntil; }
    public LocalDateTime getCreatedAt() { return this.createdAt; }
    public LocalDateTime getUpdatedAt() { return this.updatedAt; }
    
    // 명시적인 builder() 메서드 추가
    public static Builder builder() {
        return new Builder();
    }
    
    // 명시적인 Builder 클래스
    public static class Builder {
        private Long id;
        private String username;
        private String password;
        private String email;
        private String name;
        private String nickname;
        private String phoneEncrypted;
        private UserRole role = UserRole.USER;
        private UserStatus status = UserStatus.ACTIVE;
        private LocalDateTime lastLoginAt;
        private Integer loginAttempts = 0;
        private LocalDateTime lockedUntil;
        private Boolean emailVerified = false;
        private Boolean phoneVerified = false;
        private LocalDateTime passwordChangedAt;
        private LocalDateTime passwordChangeDeferredUntil;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public Builder id(Long id) { this.id = id; return this; }
        public Builder username(String username) { this.username = username; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder phoneEncrypted(String phoneEncrypted) { this.phoneEncrypted = phoneEncrypted; return this; }
        public Builder role(UserRole role) { this.role = role; return this; }
        public Builder status(UserStatus status) { this.status = status; return this; }
        public Builder lastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; return this; }
        public Builder loginAttempts(Integer loginAttempts) { this.loginAttempts = loginAttempts; return this; }
        public Builder lockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; return this; }
        public Builder emailVerified(Boolean emailVerified) { this.emailVerified = emailVerified; return this; }
        public Builder phoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; return this; }
        public Builder passwordChangedAt(LocalDateTime passwordChangedAt) { this.passwordChangedAt = passwordChangedAt; return this; }
        public Builder passwordChangeDeferredUntil(LocalDateTime passwordChangeDeferredUntil) { this.passwordChangeDeferredUntil = passwordChangeDeferredUntil; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        
        public User build() {
            User user = new User();
            user.id = this.id;
            user.username = this.username;
            user.password = this.password;
            user.email = this.email;
            user.name = this.name;
            user.nickname = this.nickname;
            user.phoneEncrypted = this.phoneEncrypted;
            user.role = this.role;
            user.status = this.status;
            user.lastLoginAt = this.lastLoginAt;
            user.loginAttempts = this.loginAttempts;
            user.lockedUntil = this.lockedUntil;
            user.emailVerified = this.emailVerified;
            user.phoneVerified = this.phoneVerified;
            user.passwordChangedAt = this.passwordChangedAt;
            user.passwordChangeDeferredUntil = this.passwordChangeDeferredUntil;
            user.createdAt = this.createdAt;
            user.updatedAt = this.updatedAt;
            return user;
        }
    }
    
    public enum UserRole {
        USER, PREMIUM_USER, ADMIN
    }
    
    public enum UserStatus {
        ACTIVE, INACTIVE, LOCKED, DELETED
    }
} 