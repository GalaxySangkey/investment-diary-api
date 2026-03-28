package com.investmentdiary.dto.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnRegisterFinishRequest {
    
    @NotBlank(message = "challenge는 필수입니다.")
    private String challenge; // 원래 받은 challenge
    
    @NotNull(message = "credential은 필수입니다.")
    @Valid
    private Credential credential; // 클라이언트에서 생성한 credential
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Credential {
        @NotBlank(message = "credential id는 필수입니다.")
        private String id; // Base64 encoded credential ID
        
        @NotBlank(message = "credential rawId는 필수입니다.")
        private String rawId; // Base64 encoded raw credential ID
        
        @NotNull(message = "credential response는 필수입니다.")
        @Valid
        private Response response; // 인증 응답
        
        private String type; // "public-key"
        private ClientExtensionResults clientExtensionResults; // 클라이언트 확장 결과
        private String authenticatorAttachment; // "platform", "cross-platform"
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        @NotBlank(message = "clientDataJSON은 필수입니다.")
        private String clientDataJSON; // Base64 encoded client data
        
        @NotBlank(message = "attestationObject는 필수입니다.")
        private String attestationObject; // Base64 encoded attestation object
        
        private String[] transports; // ["usb", "nfc", "ble", "internal"]
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    public static class ClientExtensionResults {
        // 확장 결과 (필요시 추가)
    }
}

