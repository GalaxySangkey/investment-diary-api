-- 고정 수입/지출 테이블에 기간 필드 추가

-- fixed_incomes 테이블에 start_date, end_date 컬럼 추가
ALTER TABLE fixed_incomes 
ADD COLUMN IF NOT EXISTS start_date DATE,
ADD COLUMN IF NOT EXISTS end_date DATE;

-- fixed_expenses 테이블에 start_date, end_date 컬럼 추가
ALTER TABLE fixed_expenses 
ADD COLUMN IF NOT EXISTS start_date DATE,
ADD COLUMN IF NOT EXISTS end_date DATE;

