-- V16 실패한 마이그레이션 정리 스크립트
-- 확인용 쿼리만 포함 (실행은 수동으로 진행)

-- 1. 실패한 V16 마이그레이션 레코드 확인
SELECT * FROM flyway_schema_history WHERE version = '16';

-- 2. dividend_date 컬럼이 이미 존재하는지 확인
SELECT COUNT(*) as dividend_date_exists FROM information_schema.columns 
WHERE table_schema = 'investment_diary' 
  AND table_name = 'investment_records' 
  AND column_name = 'dividend_date';

