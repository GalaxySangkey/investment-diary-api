package com.investmentdiary.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    
    @NotBlank(message = "사용자계정은 필수입니다.")
    private String username;
    
    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
    
    private String deviceInfo;
    
    // 명시적인 getter 메서드들
    public String getUsername() { return this.username; }
    public String getPassword() { return this.password; }
    public String getDeviceInfo() { return this.deviceInfo; }
}