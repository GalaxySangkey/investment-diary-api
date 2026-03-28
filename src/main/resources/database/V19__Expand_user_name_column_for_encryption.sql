-- User.name 암호화 저장을 위해 컬럼 길이 확장 (AES Base64 저장)
-- MySQL
ALTER TABLE users MODIFY COLUMN name VARCHAR(255) NOT NULL;
