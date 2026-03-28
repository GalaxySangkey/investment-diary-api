package com.investmentdiary.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * User.name 필드 DB 저장 시 암호화, 조회 시 복호화.
 * 기존 평문 데이터는 복호화 실패 시 그대로 반환하여 호환.
 *
 * 대량 조회 시: N건 조회 시 convertToEntityAttribute가 N번 호출되므로
 * 복호화는 N번 수행됨. AES 복호화는 건당 수 μs 수준으로 빠르나,
 * 수백~수천 건 목록 API에서는 목록용 DTO에서 name 제외/마스킹 고려.
 */
@Converter
@Slf4j
public class UserNameAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return null;
        }
        EncryptionUtil util = EncryptionUtilHolder.get();
        if (util == null) {
            log.warn("EncryptionUtil not set, storing name as plain text");
            return attribute;
        }
        return util.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        EncryptionUtil util = EncryptionUtilHolder.get();
        if (util == null) {
            log.warn("EncryptionUtil not set, returning name as-is");
            return dbData;
        }
        return util.decryptOrReturnPlain(dbData);
    }
}
