package com.investmentdiary.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnLoginStartResponse {
    
    private String challenge; // Base64 encoded challenge
    private Long timeout; // 타임아웃 (밀리초)
    private String rpId; // Relying Party ID
    private List<PublicKeyCredentialDescriptor> allowCredentials; // 허용된 credential 목록
    private String userVerification; // "required", "preferred", "discouraged"
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicKeyCredentialDescriptor {
        private String id; // Base64 encoded credential ID
        private String type; // "public-key"
        private String[] transports; // ["usb", "nfc", "ble", "internal"]
    }
}


