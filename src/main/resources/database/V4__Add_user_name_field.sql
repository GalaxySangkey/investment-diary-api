-- 사용자 테이블에 실제 이름(name) 필드 추가
-- MySQL
ALTER TABLE users ADD COLUMN name VARCHAR(50) NOT NULL DEFAULT '' AFTER email;

-- 기존 데이터가 있는 경우 nickname을 name으로 복사 (임시 처리)
UPDATE users SET name = nickname WHERE name = '' OR name IS NULL;

-- PostgreSQL의 경우 (MySQL과 다른 구문)
-- ALTER TABLE users ADD COLUMN name VARCHAR(50) NOT NULL DEFAULT '';
-- UPDATE users SET name = nickname WHERE name = '' OR name IS NULL;

