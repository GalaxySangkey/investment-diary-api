package com.investmentdiary.dto.investment;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SellInvestmentRequest {
    
    @NotNull(message = "매도일은 필수입니다.")
    private LocalDate recordDate;
    
    @NotNull(message = "선택된 종목 ID는 필수입니다.")
    private Long selectedStockId;
    
    @NotNull(message = "매도 수량은 필수입니다.")
    @DecimalMin(value = "0.0001", message = "매도 수량은 0.0001 이상이어야 합니다")
    private BigDecimal sellQuantity;
    
    // 매도 비율은 자동 계산되므로 nullable
    @DecimalMin(value = "0.0", message = "매도 비율은 0 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "매도 비율은 100 이하여야 합니다.")
    private BigDecimal sellRatio;
    
    @DecimalMin(value = "-100.0", message = "실현 수익률은 -100 이상이어야 합니다.")
    private BigDecimal realizedProfitRate;
    
    @DecimalMin(value = "0.0", message = "매도금액은 0 이상이어야 합니다.")
    private BigDecimal sellPrice; // 매도 단가 (주당 가격)
    
    private String sellReason;
    
    // 명시적인 getter 메서드들
    public LocalDate getRecordDate() { return this.recordDate; }
    public Long getSelectedStockId() { return this.selectedStockId; }
    public BigDecimal getSellQuantity() { return this.sellQuantity; }
    public BigDecimal getSellRatio() { return this.sellRatio; }
    public BigDecimal getRealizedProfitRate() { return this.realizedProfitRate; }
    public BigDecimal getSellPrice() { return this.sellPrice; }
    public String getSellReason() { return this.sellReason; }
}