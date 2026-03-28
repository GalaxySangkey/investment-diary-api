package com.investmentdiary.entity;

/**
 * 자산 유형 Enum
 * 주식, 외환, 채권 등 다양한 자산 유형을 구분
 */
public enum AssetType {
    STOCK,      // 주식
    CURRENCY    // 외환 (환차익투자)
    // 향후 확장 가능: BOND(채권), REAL_ESTATE(부동산), CRYPTO(암호화폐) 등
}

