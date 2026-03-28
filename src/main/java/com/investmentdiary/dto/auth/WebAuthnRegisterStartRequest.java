package com.investmentdiary.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebAuthnRegisterStartRequest {
    
    @NotBlank(message = "사용자계정은 필수입니다.")
    @Size(min = 2, max = 20, message = "사용자계정은 2-20자 사이여야 합니다.")
    @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "사용자계정은 한글, 영문, 숫자, 언더스코어만 사용 가능합니다.")
    private String username;
    
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 2, max = 50, message = "이름은 2-50자 사이여야 합니다.")
    private String name; // 실제 이름
    
    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2-20자 사이여야 합니다.")
    private String nickname;
    
    @Pattern(regexp = "^01[0-9]-[0-9]{3,4}-[0-9]{4}$", message = "올바른 전화번호 형식이 아닙니다.")
    private String phone;
    
    @Size(min = 6, max = 100, message = "비밀번호는 6-100자 사이여야 합니다.")
    private String password; // 비밀번호 (선택적, 패스키를 잃어버렸을 때 대비)
    
    private String deviceName; // 예: "iPhone 14", "Chrome on Windows"
}

