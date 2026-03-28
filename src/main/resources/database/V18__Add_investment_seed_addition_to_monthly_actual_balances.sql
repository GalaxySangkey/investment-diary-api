-- V18: monthly_actual_balances에 투자시드 증액 컬럼 추가
-- 월별로 순수 현금에서 투자시드로 전환한 금액 (해당 월 순수 현금에서 차감, 홈 총 시드에 가산)
ALTER TABLE monthly_actual_balances
ADD COLUMN investment_seed_addition DECIMAL(15,2) NULL COMMENT '해당 월 투자시드 증액 (순수 현금에서 차감)' AFTER difference;
