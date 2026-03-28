-- 투자일지 데이터베이스 스키마
-- MySQL 8.0+ / PostgreSQL 12+ 호환

-- 사용자 테이블 (개인정보 암호화)
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL, -- bcrypt로 암호화
    email VARCHAR(100) UNIQUE NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    phone_encrypted TEXT NOT NULL, -- AES 암호화된 전화번호
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_username (username),
    INDEX idx_email (email)
);

-- 사용자 세션 테이블 (로그인 로그)
CREATE TABLE user_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    device_uuid VARCHAR(100) NOT NULL,
    device_info TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_session_token (session_token),
    INDEX idx_expires_at (expires_at)
);

-- 포트폴리오 설정 테이블
CREATE TABLE portfolio_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_seed DECIMAL(15,2) NOT NULL DEFAULT 10000000.00, -- 기본 1000만원
    currency VARCHAR(3) DEFAULT 'KRW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_portfolio (user_id)
);

-- 투자 기록 테이블 (핵심 데이터)
CREATE TABLE investment_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    record_date DATE NOT NULL,
    record_type ENUM('buy', 'sell') NOT NULL,
    stock_code VARCHAR(20), -- 종목코드 (선택사항)
    stock_name VARCHAR(100) NOT NULL,
    investment_ratio DECIMAL(5,2) NOT NULL, -- 전체 시드 대비 투자 비율
    quantity INT, -- 주식 수량
    price_per_share DECIMAL(10,2), -- 주당 가격
    total_amount DECIMAL(15,2), -- 총 투자금액
    dividend_per_share DECIMAL(10,2), -- 주당 배당금
    dividend_ratio DECIMAL(5,2), -- 배당률
    buy_reason TEXT, -- 매수 이유 (암호화 가능)
    sell_reason TEXT, -- 매도 사유 (암호화 가능)
    sell_quantity INT, -- 매도 수량
    sell_ratio DECIMAL(5,2), -- 매도 비율
    realized_profit_rate DECIMAL(5,2), -- 실현 손익률
    selected_stock_id BIGINT, -- 매도 시 선택된 종목 ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (selected_stock_id) REFERENCES investment_records(id),
    INDEX idx_user_date (user_id, record_date),
    INDEX idx_stock_name (stock_name),
    INDEX idx_record_type (record_type)
);

-- 주가 데이터 캐시 테이블 (API 호출 최소화)
CREATE TABLE stock_price_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    stock_name VARCHAR(100) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    change_rate DECIMAL(5,2), -- 등락률
    volume BIGINT, -- 거래량
    market_cap DECIMAL(15,2), -- 시가총액
    dividend_yield DECIMAL(5,2), -- 배당수익률
    price_date DATE NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_stock_date (stock_code, price_date),
    INDEX idx_stock_code (stock_code),
    INDEX idx_price_date (price_date)
);

-- 일별 포트폴리오 요약 테이블 (성능 최적화)
CREATE TABLE daily_portfolio_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    summary_date DATE NOT NULL,
    total_investment DECIMAL(15,2) NOT NULL,
    total_profit_rate DECIMAL(5,2) NOT NULL,
    total_dividend_rate DECIMAL(5,2) NOT NULL,
    total_profit_amount DECIMAL(15,2) NOT NULL,
    total_dividend_amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_date (user_id, summary_date),
    INDEX idx_user_date (user_id, summary_date)
);

-- 커뮤니티 게시판 테이블
CREATE TABLE community_posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category ENUM('discussion', 'tip', 'news', 'diary') NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_anonymous BOOLEAN DEFAULT FALSE,
    view_count INT DEFAULT 0,
    like_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_category (category),
    INDEX idx_created_at (created_at),
    INDEX idx_user_id (user_id)
);

-- 커뮤니티 댓글 테이블
CREATE TABLE community_comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL, -- 대댓글 지원
    content TEXT NOT NULL,
    is_anonymous BOOLEAN DEFAULT FALSE,
    like_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES community_comments(id) ON DELETE CASCADE,
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id)
);

-- 사용자 등급 시스템 테이블
CREATE TABLE user_levels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level_name VARCHAR(50) NOT NULL,
    min_points INT NOT NULL,
    max_points INT NOT NULL,
    benefits TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 사용자 포인트 테이블
CREATE TABLE user_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points INT DEFAULT 0,
    level_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (level_id) REFERENCES user_levels(id),
    UNIQUE KEY unique_user_points (user_id)
);

-- 포인트 이력 테이블
CREATE TABLE point_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points_change INT NOT NULL, -- 양수: 획득, 음수: 차감
    reason VARCHAR(100) NOT NULL, -- 포인트 획득/차감 사유
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

-- 알림 테이블
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type ENUM('system', 'investment', 'community', 'market') NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_is_read (is_read),
    INDEX idx_created_at (created_at)
);

-- API 호출 로그 테이블 (모니터링)
CREATE TABLE api_call_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    api_endpoint VARCHAR(200) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    response_status INT NOT NULL,
    response_time_ms INT NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at),
    INDEX idx_api_endpoint (api_endpoint)
);

-- 기본 등급 데이터 삽입
INSERT INTO user_levels (level_name, min_points, max_points, benefits) VALUES
('초보 투자자', 0, 100, '기본 기능 사용'),
('성장 투자자', 101, 500, '고급 분석 기능'),
('전문 투자자', 501, 1000, '전문 도구 사용'),
('마스터 투자자', 1001, 9999, '모든 기능 사용');

-- 인덱스 최적화
CREATE INDEX IF NOT EXISTS idx_investment_records_user_type_date ON investment_records(user_id, record_type, record_date);
CREATE INDEX IF NOT EXISTS idx_stock_price_cache_updated ON stock_price_cache(last_updated);
CREATE INDEX IF NOT EXISTS idx_community_posts_category_date ON community_posts(category, created_at);
