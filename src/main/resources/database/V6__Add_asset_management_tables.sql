-- 자산관리 테이블 추가
-- V6: 자산 설정 및 월별 실제 금액 테이블 생성

-- 자산 설정 테이블 (사용자별 계산 시작 날짜 및 시작 금액)
CREATE TABLE IF NOT EXISTS asset_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    start_date DATE NOT NULL COMMENT '계산 시작 날짜',
    initial_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '시작 금액',
    savings DECIMAL(15,2) NULL COMMENT '월 저축액',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_asset_settings (user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='자산 설정 테이블';

-- savings 컬럼은 V7 마이그레이션에서 추가됨

-- 월별 실제 금액 테이블 (계산된 금액과 실제 금액의 차이 저장)
CREATE TABLE IF NOT EXISTS monthly_actual_balances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    year INT NOT NULL COMMENT '연도',
    month INT NOT NULL COMMENT '월 (1-12)',
    actual_balance DECIMAL(15,2) NULL COMMENT '실제 금액',
    difference DECIMAL(15,2) NULL COMMENT '계산된 금액과 실제 금액의 차이',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_year_month (user_id, year, month),
    INDEX idx_user_year_month (user_id, year, month),
    INDEX idx_created_at (created_at),
    CHECK (year >= 2000 AND year <= 9999),
    CHECK (month >= 1 AND month <= 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='월별 실제 금액 테이블';

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

