-- 종목 리스트 캐시 테이블 (KRX/미국 종목 검색용)
-- KRX 서버 장애 시에도 종목 검색이 가능하도록 DB에 캐시
-- 스케줄러가 주기적으로 Python 서비스를 통해 업데이트

CREATE TABLE IF NOT EXISTS stock_listings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '종목코드 (예: 005930, AAPL)',
    stock_name VARCHAR(100) NOT NULL COMMENT '종목명 (예: 삼성전자, Apple Inc)',
    market VARCHAR(20) NOT NULL COMMENT '시장 (KOSPI, KOSDAQ, KONEX, NASDAQ, NYSE)',
    ticker VARCHAR(50) COMMENT 'Yahoo Finance 티커 (예: 005930.KS, AAPL)',
    country VARCHAR(10) NOT NULL DEFAULT 'KR' COMMENT '국가 코드 (KR, US)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '활성화 여부 (상장폐지 시 FALSE)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_stock_code_country (stock_code, country),
    INDEX idx_stock_name (stock_name),
    INDEX idx_market (market),
    INDEX idx_country (country),
    INDEX idx_is_active (is_active),
    INDEX idx_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='종목 리스트 캐시 (종목 검색용)';
