-- API 요청 큐 테이블 생성
-- V3: 메시지 큐 시스템을 위한 테이블 추가

CREATE TABLE api_request_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL UNIQUE COMMENT '요청 ID (UUID)',
    user_id BIGINT NULL COMMENT '사용자 ID',
    endpoint VARCHAR(255) NOT NULL COMMENT 'API 엔드포인트',
    method VARCHAR(10) NOT NULL COMMENT 'HTTP 메서드 (POST, GET, PUT, DELETE)',
    request_body TEXT NULL COMMENT '요청 본문 (JSON)',
    request_headers TEXT NULL COMMENT '요청 헤더 (JSON)',
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'RETRYING') NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    max_retries INT NOT NULL DEFAULT 3 COMMENT '최대 재시도 횟수',
    result_body TEXT NULL COMMENT '처리 결과 (JSON)',
    error_message TEXT NULL COMMENT '에러 메시지',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시간',
    processed_at TIMESTAMP NULL COMMENT '처리 완료 시간',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시간',
    INDEX idx_request_id (request_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_user_id (user_id),
    INDEX idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='API 요청 큐 테이블 - 서버 장애 시에도 요청이 누락되지 않도록 보장';


