-- 외환거래 지원을 위한 필드 추가
-- 자산 유형 구분 (주식/외환)

-- asset_type 컬럼 추가 (이미 존재하면 스킵)
-- 실제 컬럼명 확인: JPA는 'type'을 사용하지만 V1 스키마는 'record_type'을 사용
-- 안전하게 record_date 다음에 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND column_name = 'asset_type');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN asset_type VARCHAR(20) NOT NULL DEFAULT ''STOCK'' AFTER record_date', 
              'SELECT ''Column asset_type already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- currency_pair 컬럼 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND column_name = 'currency_pair');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN currency_pair VARCHAR(20) NULL COMMENT ''통화쌍 (예: USD/KRW, EUR/USD)'' AFTER stock_code', 
              'SELECT ''Column currency_pair already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- base_currency 컬럼 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND column_name = 'base_currency');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN base_currency VARCHAR(3) NULL COMMENT ''기준 통화 (예: USD)'' AFTER currency_pair', 
              'SELECT ''Column base_currency already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- quote_currency 컬럼 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND column_name = 'quote_currency');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN quote_currency VARCHAR(3) NULL COMMENT ''상대 통화 (예: KRW)'' AFTER base_currency', 
              'SELECT ''Column quote_currency already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- exchange_rate 컬럼 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND column_name = 'exchange_rate');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN exchange_rate DECIMAL(12,4) NULL COMMENT ''환율'' AFTER quote_currency', 
              'SELECT ''Column exchange_rate already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 인덱스 추가 (이미 존재하면 스킵)
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND index_name = 'idx_asset_type');
SET @sql = IF(@idx_exists = 0, 
              'CREATE INDEX idx_asset_type ON investment_records(asset_type)', 
              'SELECT ''Index idx_asset_type already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND index_name = 'idx_currency_pair');
SET @sql = IF(@idx_exists = 0, 
              'CREATE INDEX idx_currency_pair ON investment_records(currency_pair)', 
              'SELECT ''Index idx_currency_pair already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

