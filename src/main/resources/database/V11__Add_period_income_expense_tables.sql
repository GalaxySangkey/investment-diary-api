-- V11: 기간별 수입/지출 테이블 생성

-- 기간별 수입 테이블
CREATE TABLE IF NOT EXISTS period_incomes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '수입 항목명',
    amount DECIMAL(15,2) NOT NULL COMMENT '금액',
    start_date DATE NOT NULL COMMENT '시작 날짜',
    end_date DATE NOT NULL COMMENT '종료 날짜',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_start_date (start_date),
    INDEX idx_end_date (end_date),
    INDEX idx_created_at (created_at),
    CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='기간별 수입 테이블';

-- 기간별 지출 테이블
CREATE TABLE IF NOT EXISTS period_expenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT '지출 항목명',
    amount DECIMAL(15,2) NOT NULL COMMENT '금액',
    start_date DATE NOT NULL COMMENT '시작 날짜',
    end_date DATE NOT NULL COMMENT '종료 날짜',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_start_date (start_date),
    INDEX idx_end_date (end_date),
    INDEX idx_created_at (created_at),
    CHECK (end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='기간별 지출 테이블';

