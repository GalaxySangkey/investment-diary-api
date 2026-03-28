-- 주식 종가, 배당금, 환율 데이터 저장 테이블
-- Yahoo Finance API를 통해 조회한 데이터를 저장

-- 주식 종가 테이블 (매일 업데이트)
CREATE TABLE IF NOT EXISTS stock_prices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(50) NOT NULL COMMENT 'Yahoo Finance 티커 (예: 005930.KS, AAPL)',
    stock_code VARCHAR(20) COMMENT '종목코드 (한국 주식의 경우)',
    stock_name VARCHAR(100) NOT NULL COMMENT '종목명',
    price_date DATE NOT NULL COMMENT '가격 날짜',
    close_price DECIMAL(15,4) NOT NULL COMMENT '종가',
    open_price DECIMAL(15,4) COMMENT '시가',
    high_price DECIMAL(15,4) COMMENT '고가',
    low_price DECIMAL(15,4) COMMENT '저가',
    volume BIGINT COMMENT '거래량',
    currency VARCHAR(3) DEFAULT 'KRW' COMMENT '통화 (KRW, USD, EUR 등)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_ticker_date (ticker, price_date),
    INDEX idx_ticker (ticker),
    INDEX idx_stock_code (stock_code),
    INDEX idx_price_date (price_date),
    INDEX idx_stock_name (stock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주식 종가 데이터 (매일 업데이트)';

-- 배당금 테이블 (매월 업데이트)
CREATE TABLE IF NOT EXISTS stock_dividends (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(50) NOT NULL COMMENT 'Yahoo Finance 티커',
    stock_code VARCHAR(20) COMMENT '종목코드',
    stock_name VARCHAR(100) NOT NULL COMMENT '종목명',
    dividend_date DATE NOT NULL COMMENT '배당 지급일',
    dividend_per_share DECIMAL(15,4) NOT NULL COMMENT '주당 배당금',
    dividend_yield DECIMAL(8,4) COMMENT '배당수익률 (%)',
    annual_dividend DECIMAL(15,4) COMMENT '연간 배당금',
    currency VARCHAR(3) DEFAULT 'KRW' COMMENT '통화',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_ticker_dividend_date (ticker, dividend_date),
    INDEX idx_ticker (ticker),
    INDEX idx_stock_code (stock_code),
    INDEX idx_dividend_date (dividend_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='배당금 데이터 (매월 업데이트)';

-- 환율 테이블 (매일 업데이트)
CREATE TABLE IF NOT EXISTS exchange_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_pair VARCHAR(10) NOT NULL COMMENT '통화쌍 (예: USDKRW, EURKRW)',
    base_currency VARCHAR(3) NOT NULL COMMENT '기준 통화 (예: USD)',
    quote_currency VARCHAR(3) NOT NULL COMMENT '상대 통화 (예: KRW)',
    rate_date DATE NOT NULL COMMENT '환율 날짜',
    rate DECIMAL(15,4) NOT NULL COMMENT '환율',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_pair_date (currency_pair, rate_date),
    INDEX idx_currency_pair (currency_pair),
    INDEX idx_rate_date (rate_date),
    INDEX idx_base_currency (base_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='환율 데이터 (매일 업데이트)';

-- 주식 티커 매핑 테이블 (한국 주식 코드 → Yahoo Finance 티커)
CREATE TABLE IF NOT EXISTS stock_ticker_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL COMMENT '종목코드',
    stock_name VARCHAR(100) NOT NULL COMMENT '종목명',
    ticker VARCHAR(50) NOT NULL COMMENT 'Yahoo Finance 티커',
    market VARCHAR(50) COMMENT '시장 (KOSPI, KOSDAQ, NASDAQ, NYSE 등)',
    country VARCHAR(10) DEFAULT 'KR' COMMENT '국가 코드 (KR, US, JP 등)',
    is_active BOOLEAN DEFAULT TRUE COMMENT '활성화 여부',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_stock_code (stock_code),
    UNIQUE KEY unique_ticker (ticker),
    INDEX idx_stock_name (stock_name),
    INDEX idx_market (market),
    INDEX idx_country (country)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주식 티커 매핑 테이블';

