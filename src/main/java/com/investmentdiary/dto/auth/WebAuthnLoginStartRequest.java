package com.investmentdiary.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnLoginStartRequest {
    
    // 사용자명은 선택사항 (없으면 모든 credential 허용)
    private String username;
}

