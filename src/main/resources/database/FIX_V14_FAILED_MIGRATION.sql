-- V14 실패한 마이그레이션 정리 스크립트
-- 이 스크립트는 수동으로 실행해야 합니다 (Flyway 마이그레이션이 아님)
-- MySQL 클라이언트에서 직접 실행하세요

-- 1. 실패한 V14 마이그레이션 레코드 확인
SELECT * FROM flyway_schema_history WHERE version = '14';

-- 2. 컬럼이 이미 추가되어 있는지 확인
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM information_schema.columns 
WHERE table_schema = 'investment_diary' 
  AND table_name = 'investment_records' 
  AND COLUMN_NAME IN ('asset_type', 'currency_pair', 'base_currency', 'quote_currency', 'exchange_rate');

-- 3. 컬럼이 이미 추가되어 있다면, 실패한 V14 마이그레이션 레코드 삭제
-- 주의: 컬럼이 이미 추가되어 있는 경우에만 실행하세요
DELETE FROM flyway_schema_history WHERE version = '14' AND success = 0;

-- 4. 또는 V14 레코드를 완전히 삭제하고 다시 실행하려면 (컬럼이 없을 때)
-- 먼저 컬럼이 있는지 확인한 후, 없으면 아래 명령 실행
-- DELETE FROM flyway_schema_history WHERE version = '14';

-- 5. 컬럼이 부분적으로만 추가된 경우, 누락된 컬럼만 추가
-- asset_type이 없으면 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = 'investment_diary' 
                   AND table_name = 'investment_records' 
                   AND column_name = 'asset_type');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN asset_type VARCHAR(20) NOT NULL DEFAULT ''STOCK'' AFTER record_date', 
              'SELECT ''Column asset_type already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- currency_pair가 없으면 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = 'investment_diary' 
                   AND table_name = 'investment_records' 
                   AND column_name = 'currency_pair');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN currency_pair VARCHAR(20) NULL AFTER stock_code', 
              'SELECT ''Column currency_pair already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- base_currency가 없으면 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = 'investment_diary' 
                   AND table_name = 'investment_records' 
                   AND column_name = 'base_currency');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN base_currency VARCHAR(3) NULL AFTER currency_pair', 
              'SELECT ''Column base_currency already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- quote_currency가 없으면 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = 'investment_diary' 
                   AND table_name = 'investment_records' 
                   AND column_name = 'quote_currency');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN quote_currency VARCHAR(3) NULL AFTER base_currency', 
              'SELECT ''Column quote_currency already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- exchange_rate가 없으면 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = 'investment_diary' 
                   AND table_name = 'investment_records' 
                   AND column_name = 'exchange_rate');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN exchange_rate DECIMAL(12,4) NULL AFTER quote_currency', 
              'SELECT ''Column exchange_rate already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

