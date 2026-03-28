package com.investmentdiary.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * 민감 텍스트 컬럼 공용 컨버터.
 * - 저장 시 암호화
 * - 조회 시 복호화(복호화 실패 시 평문 그대로 반환하여 기존 데이터 호환)
 *
 * 대량 데이터의 일괄 마이그레이션 없이 점진 적용(lazy)할 때 사용한다.
 */
@Converter
@Slf4j
public class SensitiveTextAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isBlank()) {
            return null;
        }
        EncryptionUtil util = EncryptionUtilHolder.get();
        if (util == null) {
            log.warn("EncryptionUtil not set, storing sensitive text as plain text");
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
            log.warn("EncryptionUtil not set, returning sensitive text as-is");
            return dbData;
        }
        return util.decryptOrReturnPlain(dbData);
    }
}
