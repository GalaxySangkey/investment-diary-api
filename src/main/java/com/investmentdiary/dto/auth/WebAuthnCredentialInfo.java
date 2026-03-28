package com.investmentdiary.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnCredentialInfo {
    private Long id;
    private String deviceName;
    private String authenticatorAttachment; // "platform", "cross-platform"
    private String transports; // "internal", "usb", "nfc", "ble", "hybrid"
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}




