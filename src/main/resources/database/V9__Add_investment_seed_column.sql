-- V9: asset_settings 테이블에 investment_seed 컬럼 추가
-- 기존 테이블에 컬럼이 없을 경우에만 추가

-- 컬럼 존재 여부 확인 후 추가
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
               WHERE TABLE_SCHEMA = DATABASE() 
               AND TABLE_NAME = 'asset_settings' 
               AND COLUMN_NAME = 'investment_seed');

SET @sqlstmt := IF(@exist = 0, 
                   'ALTER TABLE asset_settings ADD COLUMN investment_seed DECIMAL(15,2) NULL COMMENT ''투자시드''',
                   'SELECT 1 AS column_already_exists');

PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

