package com.investmentdiary.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "암호화 결과 응답")
public class EncryptResponse {

    @Schema(description = "현재 설정된 암호화 키로 암호화된 Base64 문자열 (DB 등에 그대로 저장 가능)")
    private String encrypted;
}
