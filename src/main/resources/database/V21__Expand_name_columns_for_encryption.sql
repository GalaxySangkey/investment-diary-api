-- 민감 텍스트(name) 암호화 저장을 위해 컬럼 길이 확장
ALTER TABLE fixed_incomes MODIFY COLUMN name VARCHAR(255) NOT NULL;
ALTER TABLE fixed_expenses MODIFY COLUMN name VARCHAR(255) NOT NULL;
ALTER TABLE period_incomes MODIFY COLUMN name VARCHAR(255) NOT NULL;
ALTER TABLE period_expenses MODIFY COLUMN name VARCHAR(255) NOT NULL;
