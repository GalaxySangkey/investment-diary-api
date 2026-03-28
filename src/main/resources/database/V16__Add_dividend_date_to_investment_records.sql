-- investment_records 테이블에 dividend_date 컬럼 추가
-- 컬럼이 이미 존재하는 경우를 대비하여 조건부 추가
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns 
                   WHERE table_schema = DATABASE() 
                   AND table_name = 'investment_records' 
                   AND column_name = 'dividend_date');
SET @sql = IF(@col_exists = 0, 
              'ALTER TABLE investment_records ADD COLUMN dividend_date DATE NULL COMMENT ''배당 지급일'' AFTER dividend_per_share', 
              'SELECT ''Column dividend_date already exists'' AS message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

