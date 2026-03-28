-- 비밀번호 변경 주기(6개월 권고), 3개월 유예, 최근 3개 재사용 방지
ALTER TABLE users
    ADD COLUMN password_changed_at DATETIME(6) NULL COMMENT '마지막 비밀번호 변경 시각' AFTER phone_verified,
    ADD COLUMN password_change_deferred_until DATETIME(6) NULL COMMENT '비밀번호 변경 권고 유예 만료 시각' AFTER password_changed_at;

UPDATE users
SET password_changed_at = created_at
WHERE password IS NOT NULL AND TRIM(password) <> '' AND password_changed_at IS NULL;

CREATE TABLE user_password_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_password_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_uph_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
