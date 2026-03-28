package com.investmentdiary.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserInfo user;

    /** 비밀번호 계정만: 마지막 변경 후 6개월 경과 등으로 변경 권고가 필요한지 */
    private boolean passwordChangeRecommended;
    /** 비밀번호 변경 권고가 시작되는 기준 시각(마지막 변경 + 6개월) */
    private LocalDateTime passwordChangeDueAt;
    /** 사용자가 3개월 유예를 선택한 경우 그 만료 시각 */
    private LocalDateTime passwordChangeDeferredUntil;

    public LoginResponse(String accessToken, String refreshToken, long expiresIn, UserInfo user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.user = user;
        this.passwordChangeRecommended = false;
        this.passwordChangeDueAt = null;
        this.passwordChangeDeferredUntil = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private UserInfo user;
        private boolean passwordChangeRecommended;
        private LocalDateTime passwordChangeDueAt;
        private LocalDateTime passwordChangeDeferredUntil;

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder expiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder user(UserInfo user) {
            this.user = user;
            return this;
        }

        public Builder passwordChangeRecommended(boolean passwordChangeRecommended) {
            this.passwordChangeRecommended = passwordChangeRecommended;
            return this;
        }

        public Builder passwordChangeDueAt(LocalDateTime passwordChangeDueAt) {
            this.passwordChangeDueAt = passwordChangeDueAt;
            return this;
        }

        public Builder passwordChangeDeferredUntil(LocalDateTime passwordChangeDeferredUntil) {
            this.passwordChangeDeferredUntil = passwordChangeDeferredUntil;
            return this;
        }

        public LoginResponse build() {
            LoginResponse r = new LoginResponse();
            r.setAccessToken(accessToken);
            r.setRefreshToken(refreshToken);
            r.setExpiresIn(expiresIn);
            r.setUser(user);
            r.setPasswordChangeRecommended(passwordChangeRecommended);
            r.setPasswordChangeDueAt(passwordChangeDueAt);
            r.setPasswordChangeDeferredUntil(passwordChangeDeferredUntil);
            return r;
        }
    }
}
