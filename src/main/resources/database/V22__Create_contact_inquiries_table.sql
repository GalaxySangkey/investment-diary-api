-- 문의하기(비로그인 포함) 접수 저장
CREATE TABLE IF NOT EXISTS contact_inquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '문의자 이름',
    email VARCHAR(255) NOT NULL COMMENT '회신용 이메일',
    category VARCHAR(100) NOT NULL COMMENT '문의 유형(제목)',
    message TEXT NOT NULL COMMENT '문의 본문',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '접수 시각',
    INDEX idx_contact_created_at (created_at),
    INDEX idx_contact_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='문의 접수';
