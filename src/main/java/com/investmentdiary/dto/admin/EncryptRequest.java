package com.investmentdiary.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "암호화할 평문 요청")
public class EncryptRequest {

    @NotBlank(message = "암호화할 문자열을 입력하세요.")
    @Schema(description = "암호화할 평문", example = "홍길동", required = true)
    private String plainText;
}
