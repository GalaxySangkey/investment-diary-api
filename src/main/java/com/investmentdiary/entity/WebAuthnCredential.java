package com.investmentdiary.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "webauthn_credentials", indexes = {
    @Index(name = "idx_credential_id", columnList = "credential_id", unique = true),
    @Index(name = "idx_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredential {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "credential_id", nullable = false, unique = true, length = 500)
    private String credentialId; // Base64 encoded credential ID
    
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey; // Base64 encoded public key
    
    @Column(name = "counter", nullable = false)
    private Long counter = 0L;
    
    @Column(name = "device_name", length = 100)
    private String deviceName; // 예: "iPhone 14", "Chrome on Windows"
    
    @Column(name = "authenticator_attachment", length = 20)
    private String authenticatorAttachment; // "platform" (Face ID, Touch ID 등), "cross-platform" (USB 키 등)
    
    @Column(name = "transports", length = 100)
    private String transports; // JSON 배열 문자열: ["internal", "usb", "nfc", "ble"]
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 명시적인 getter 메서드들
    public Long getId() { return this.id; }
    public User getUser() { return this.user; }
    public String getCredentialId() { return this.credentialId; }
    public String getPublicKey() { return this.publicKey; }
    public Long getCounter() { return this.counter; }
    public String getDeviceName() { return this.deviceName; }
    public String getAuthenticatorAttachment() { return this.authenticatorAttachment; }
    public String getTransports() { return this.transports; }
    public LocalDateTime getLastUsedAt() { return this.lastUsedAt; }
    public LocalDateTime getCreatedAt() { return this.createdAt; }
    public LocalDateTime getUpdatedAt() { return this.updatedAt; }
    
    // 비즈니스 메서드
    public void incrementCounter() {
        this.counter++;
        this.lastUsedAt = LocalDateTime.now();
    }
}

