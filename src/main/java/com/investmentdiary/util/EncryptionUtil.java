package com.investmentdiary.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

@Component
public class EncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtil.class);

    /** 알고리즘: AES (Java 기본값 AES/ECB/PKCS5Padding), 128비트 키 */
    private static final String ALGORITHM = "AES";
    private static final int KEY_LENGTH = 16; // 128비트

    @Value("${encryption.key}")
    private String encryptionKey;

    /** 키 파생 결과 캐시 (매 요청마다 SHA-256 해시 반복 방지) */
    private volatile SecretKeySpec cachedKey;

    private SecretKeySpec getOrCreateKey() throws Exception {
        if (cachedKey != null) {
            return cachedKey;
        }
        synchronized (this) {
            if (cachedKey != null) return cachedKey;
            byte[] key = encryptionKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, KEY_LENGTH);
            cachedKey = new SecretKeySpec(key, ALGORITHM);
            return cachedKey;
        }
    }
    
    /**
     * 데이터 암호화
     */
    public String encrypt(String data) {
        if (data == null || data.trim().isEmpty()) {
            return null;
        }
        
        try {
            SecretKeySpec secretKey = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
            
        } catch (Exception e) {
            log.error("데이터 암호화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("데이터 암호화에 실패했습니다.", e);
        }
    }
    
    /**
     * 데이터 복호화 (실패 시 원본 반환 - 기존 평문 데이터 호환용)
     */
    public String decryptOrReturnPlain(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return null;
        }
        // 평문 호환: Base64 형태가 아니면 복호화 시도 없이 그대로 반환
        if (!isLikelyBase64(encryptedData)) {
            return encryptedData;
        }
        try {
            return decrypt(encryptedData);
        } catch (Exception e) {
            log.debug("복호화 불가(평문으로 간주): {}", e.getMessage());
            return encryptedData;
        }
    }
    
    /**
     * 데이터 복호화
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.trim().isEmpty()) {
            return null;
        }
        
        try {
            SecretKeySpec secretKey = getOrCreateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("데이터 복호화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("데이터 복호화에 실패했습니다.", e);
        }
    }

    /**
     * 데이터 마스킹 (전화번호 등)
     */
    public String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return phoneNumber;
        }
        
        int length = phoneNumber.length();
        int maskLength = length - 4;
        String mask = "*".repeat(maskLength);
        return phoneNumber.substring(0, 2) + mask + phoneNumber.substring(length - 2);
    }
    
    /**
     * 이메일 마스킹
     */
    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 2) {
            return email;
        }
        
        String maskedUsername = username.substring(0, 2) + "*".repeat(username.length() - 2);
        return maskedUsername + "@" + domain;
    }

    private boolean isLikelyBase64(String value) {
        String trimmed = value.trim();
        // Base64 문자는 A-Z a-z 0-9 + / = 만 허용
        // 길이는 일반적으로 4의 배수(패딩 포함)
        if (trimmed.length() < 8 || (trimmed.length() % 4) != 0) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok =
                (c >= 'A' && c <= 'Z') ||
                (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '+' || c == '/' || c == '=';
            if (!ok) return false;
        }
        return true;
    }
} 