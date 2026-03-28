-- 수동 테이블 생성 스크립트
-- V6 마이그레이션이 실행되지 않았거나 실패한 경우 이 스크립트를 직접 실행하세요
-- MySQL 클라이언트에서 investment_diary 데이터베이스에 연결한 후 실행

USE investment_diary;

-- 고정 수입 테이블
CREATE TABLE IF NOT EXISTS fixed_incomes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '수입 항목명',
    amount DECIMAL(15,2) NOT NULL COMMENT '금액',
    day INT NOT NULL COMMENT '매월 며칠에 받는지 (1-31)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    CHECK (day >= 1 AND day <= 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='고정 수입 테이블';

-- 고정 지출 테이블
CREATE TABLE IF NOT EXISTS fixed_expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '지출 항목명',
    amount DECIMAL(15,2) NOT NULL COMMENT '금액',
    day INT NOT NULL COMMENT '매월 며칠에 지출하는지 (1-31)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    CHECK (day >= 1 AND day <= 31)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='고정 지출 테이블';

-- asset_settings 테이블에 savings 컬럼 추가 (없는 경우)
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
               WHERE TABLE_SCHEMA = 'investment_diary'
               AND TABLE_NAME = 'asset_settings' 
               AND COLUMN_NAME = 'savings');

SET @sqlstmt := IF(@exist = 0, 
                   'ALTER TABLE asset_settings ADD COLUMN savings DECIMAL(15,2) NULL COMMENT ''월 저축액''',
                   'SELECT 1 AS column_already_exists');

PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

