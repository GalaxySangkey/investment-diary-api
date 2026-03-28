package com.investmentdiary.dto.investment;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteInvestmentRequest {
    
    @NotNull(message = "투자 기록 ID는 필수입니다")
    private Long id;
    
    // 명시적인 getter 메서드 (Lombok 대신)
    public Long getId() {
        return this.id;
    }
    
    // 명시적인 setter 메서드
    public void setId(Long id) {
        this.id = id;
    }
}
