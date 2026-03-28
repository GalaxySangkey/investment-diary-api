-- WebAuthn 지원을 위한 데이터베이스 마이그레이션
-- V2: WebAuthn Credential 테이블 추가 및 User 테이블 수정

-- 1. users 테이블의 password 컬럼을 nullable로 변경
ALTER TABLE users 
MODIFY COLUMN password VARCHAR(255) NULL COMMENT 'WebAuthn 사용으로 nullable로 변경 (레거시 지원용)';

-- 2. WebAuthn Credential 테이블 생성
CREATE TABLE webauthn_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    credential_id VARCHAR(500) NOT NULL UNIQUE COMMENT 'Base64 encoded credential ID',
    public_key TEXT NOT NULL COMMENT 'Base64 encoded public key',
    counter BIGINT NOT NULL DEFAULT 0 COMMENT 'Signature counter (replay attack 방지)',
    device_name VARCHAR(100) NULL COMMENT '디바이스 이름 (예: iPhone 14, Chrome on Windows)',
    last_used_at TIMESTAMP NULL COMMENT '마지막 사용 시간',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_credential_id (credential_id),
    INDEX idx_user_id (user_id),
    INDEX idx_last_used_at (last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='WebAuthn 인증기 정보 저장 테이블';


