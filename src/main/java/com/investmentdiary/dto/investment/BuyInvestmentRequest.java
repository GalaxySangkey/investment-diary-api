package com.investmentdiary.dto.investment;

import com.investmentdiary.entity.AssetType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BuyInvestmentRequest {
    
    @NotNull(message = "투자일은 필수입니다.")
    private LocalDate recordDate;
    
    private AssetType assetType = AssetType.STOCK; // 기본값: 주식
    
    // 주식일 때 필수, 외환일 때는 선택 (통화쌍 사용)
    private String stockName;
    
    private String stockCode; // 주식일 때만 사용
    
    // 외환거래 필드
    private String currencyPair; // 통화쌍 (예: USD/KRW) - 외환일 때 필수
    private String baseCurrency; // 기준 통화 (예: USD) - 외환일 때 필수
    private String quoteCurrency; // 상대 통화 (예: KRW) - 외환일 때 필수
    private BigDecimal exchangeRate; // 환율 - 외환일 때 필수
    
    @NotNull(message = "투자비중은 필수입니다.")
    @DecimalMin(value = "0.0", message = "투자비중은 0 이상이어야 합니다.")
    @DecimalMax(value = "100.0", message = "투자비중은 100 이하여야 합니다.")
    private BigDecimal investmentRatio;
    
    // 수량은 선택사항 (주식/외환 모두)
    @DecimalMin(value = "0.0001", message = "수량은 0.0001 이상이어야 합니다")
    private BigDecimal quantity;
    
    // 주당 가격은 주식일 때만 사용, 외환일 때는 exchangeRate 사용
    private BigDecimal pricePerShare;
    
    @DecimalMin(value = "0.0", message = "주당 배당금은 0 이상이어야 합니다.")
    private BigDecimal dividendPerShare;
    
    private String buyReason;
    
    // 명시적인 getter 메서드들
    public LocalDate getRecordDate() { return this.recordDate; }
    public String getStockName() { return this.stockName; }
    public String getStockCode() { return this.stockCode; }
    public BigDecimal getInvestmentRatio() { return this.investmentRatio; }
    public BigDecimal getQuantity() { return this.quantity; }
    public BigDecimal getPricePerShare() { return this.pricePerShare; }
    public BigDecimal getDividendPerShare() { return this.dividendPerShare; }
    public String getBuyReason() { return this.buyReason; }
}