package com.investmentdiary.controller;

import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.dto.admin.EncryptRequest;
import com.investmentdiary.dto.admin.EncryptResponse;
import com.investmentdiary.util.EncryptionUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "관리자", description = "관리자 전용 API (ADMIN 역할 필요)")
public class AdminController {

    private final EncryptionUtil encryptionUtil;

    public AdminController(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    @PostMapping("/encrypt")
    @Operation(
        summary = "텍스트 암호화",
        description = "현재 설정된 암호화 키로 입력한 평문을 암호화한 Base64 문자열을 반환합니다. " +
            "DB에 암호화되지 않은 값이나 키 변경으로 복호화되지 않는 값을 임시로 치환할 때 Swagger에서 호출해 사용할 수 있습니다."
    )
    public UnifiedApiResponse<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
        String encrypted = encryptionUtil.encrypt(request.getPlainText());
        return UnifiedApiResponse.success(new EncryptResponse(encrypted), "암호화되었습니다.");
    }
}
