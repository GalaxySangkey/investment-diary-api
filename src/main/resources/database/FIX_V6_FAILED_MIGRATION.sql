-- V6 실패한 마이그레이션 정리 스크립트
-- 이 스크립트는 수동으로 실행해야 합니다 (Flyway 마이그레이션이 아님)
-- MySQL 클라이언트에서 직접 실행하세요

-- 1. 실패한 V6 마이그레이션 레코드 확인
SELECT * FROM flyway_schema_history WHERE version = '6';

-- 2. 테이블이 이미 생성되어 있는지 확인
SELECT COUNT(*) as asset_settings_exists FROM information_schema.tables 
WHERE table_schema = 'investment_diary' AND table_name = 'asset_settings';

SELECT COUNT(*) as monthly_balances_exists FROM information_schema.tables 
WHERE table_schema = 'investment_diary' AND table_name = 'monthly_actual_balances';

-- 3. 테이블이 이미 생성되어 있다면, 실패한 V6 마이그레이션 레코드 삭제
-- 주의: 테이블이 이미 생성되어 있는 경우에만 실행하세요
DELETE FROM flyway_schema_history WHERE version = '6' AND success = 0;

-- 4. 또는 V6 레코드를 완전히 삭제하고 다시 실행하려면 (테이블이 없을 때)
-- DELETE FROM flyway_schema_history WHERE version = '6';
