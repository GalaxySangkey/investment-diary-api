package com.investmentdiary.util;

/**
 * JPA AttributeConverter 등에서 EncryptionUtil을 사용하기 위한 정적 홀더.
 * 애플리케이션 기동 시 EncryptionConfig에서 설정한다.
 */
public final class EncryptionUtilHolder {

    private static volatile EncryptionUtil instance;

    public static void set(EncryptionUtil util) {
        instance = util;
    }

    public static EncryptionUtil get() {
        return instance;
    }

    private EncryptionUtilHolder() {}
}
