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
public class WebAuthnRegisterStartResponse {
    
    private String challenge; // Base64 encoded challenge
    private RpInfo rp; // Relying Party 정보
    private UserInfo user; // 사용자 정보
    private List<PubKeyCredParam> pubKeyCredParams; // 지원하는 알고리즘 목록
    private Long timeout; // 타임아웃 (밀리초)
    private String attestation; // "none", "direct", "indirect"
    private AuthenticatorSelection authenticatorSelection; // 인증기 선택 옵션
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RpInfo {
        private String id; // Relying Party ID (도메인)
        private String name; // Relying Party 이름
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String id; // Base64 encoded user ID
        private String name; // 사용자계정
        private String displayName; // 표시 이름
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PubKeyCredParam {
        private String type; // "public-key"
        private Long alg; // 알고리즘 ID (예: -7 for ES256)
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthenticatorSelection {
        private String authenticatorAttachment; // "platform", "cross-platform", null
        private String userVerification; // "required", "preferred", "discouraged"
        private String residentKey; // "required", "preferred", "discouraged"
    }
}

