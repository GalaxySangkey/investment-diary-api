-- WebAuthn Credential 테이블에 authenticatorAttachment와 transports 필드 추가
-- V5: Face ID, Touch ID 등 platform authenticator 지원을 위한 필드 추가

ALTER TABLE webauthn_credentials 
ADD COLUMN authenticator_attachment VARCHAR(20) NULL COMMENT '인증기 타입: "platform" (Face ID, Touch ID 등), "cross-platform" (USB 키 등)' AFTER device_name,
ADD COLUMN transports VARCHAR(100) NULL COMMENT '지원하는 transport 목록: "internal", "usb", "nfc", "ble" (쉼표로 구분)' AFTER authenticator_attachment;

-- 기존 데이터에 대한 기본값 설정
-- platform authenticator로 가정하고 "internal" transport 설정
UPDATE webauthn_credentials 
SET authenticator_attachment = 'platform', transports = 'internal' 
WHERE authenticator_attachment IS NULL;



