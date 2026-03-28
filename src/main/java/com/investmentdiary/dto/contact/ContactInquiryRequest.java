package com.investmentdiary.dto.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactInquiryRequest {

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 255, message = "이메일이 너무 깁니다.")
    private String email;

    @NotBlank(message = "문의 유형을 선택해주세요.")
    @Size(max = 100, message = "문의 유형이 너무 깁니다.")
    private String subject;

    @NotBlank(message = "문의 내용을 입력해주세요.")
    @Size(max = 10000, message = "문의 내용은 10000자 이하여야 합니다.")
    private String message;
}
