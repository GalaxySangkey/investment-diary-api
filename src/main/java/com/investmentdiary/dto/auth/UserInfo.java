package com.investmentdiary.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserInfo {
    private Long id;
    private String username;
    private String name; // 실제 이름
    private String nickname;
    private String email;
    
    // 생성자 수동 구현
    public UserInfo(Long id, String username, String name, String nickname, String email) {
        this.id = id;
        this.username = username;
        this.name = name;
        this.nickname = nickname;
        this.email = email;
    }
    
    // Builder 패턴 수동 구현
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Long id;
        private String username;
        private String name;
        private String nickname;
        private String email;
        
        public Builder id(Long id) { 
            this.id = id; 
            return this; 
        }
        
        public Builder username(String username) { 
            this.username = username; 
            return this; 
        }
        
        public Builder nickname(String nickname) { 
            this.nickname = nickname; 
            return this; 
        }
        
        public Builder name(String name) { 
            this.name = name; 
            return this; 
        }
        
        public Builder email(String email) { 
            this.email = email; 
            return this; 
        }
        
        public UserInfo build() {
            return new UserInfo(id, username, name, nickname, email);
        }
    }
}
