package com.investmentdiary.dto.investment;

import com.investmentdiary.entity.AssetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateInvestmentRequest {
    
    @NotNull(message = "투자 기록 ID는 필수입니다")
    private Long id;
    
    private AssetType assetType;
    
    private String stockName;
    
    private String stockCode;
    
    private String currencyPair;
    
    private String baseCurrency;
    
    private String quoteCurrency;
    
    private BigDecimal exchangeRate;
    
    @Positive(message = "투자 비율은 양수여야 합니다")
    private BigDecimal investmentRatio;
    
    @DecimalMin(value = "0.0001", message = "수량은 0.0001 이상이어야 합니다")
    private BigDecimal quantity;
    
    @Positive(message = "주당 가격은 양수여야 합니다")
    private BigDecimal pricePerShare;
    
    @PositiveOrZero(message = "총 투자 금액은 0 이상이어야 합니다")
    private BigDecimal totalAmount;
    
    @Size(max = 1000, message = "매수 이유는 1000자 이하여야 합니다")
    private String buyReason;
    
    @Size(max = 1000, message = "매도 이유는 1000자 이하여야 합니다")
    private String sellReason;
    
    @DecimalMin(value = "0.0001", message = "매도 수량은 0.0001 이상이어야 합니다")
    private BigDecimal sellQuantity;
    
    @Positive(message = "매도 비율은 양수여야 합니다")
    private BigDecimal sellRatio;
    
    private BigDecimal realizedProfitRate;
    
    @Positive(message = "매도 단가는 양수여야 합니다")
    private BigDecimal sellPrice; // 매도 단가 (주당 가격)
    
    // 명시적인 getter 메서드들 (Lombok 대신)
    public Long getId() {
        return this.id;
    }
    
    public AssetType getAssetType() {
        return this.assetType;
    }
    
    public String getStockName() {
        return this.stockName;
    }
    
    public String getStockCode() {
        return this.stockCode;
    }
    
    public String getCurrencyPair() {
        return this.currencyPair;
    }
    
    public String getBaseCurrency() {
        return this.baseCurrency;
    }
    
    public String getQuoteCurrency() {
        return this.quoteCurrency;
    }
    
    public BigDecimal getExchangeRate() {
        return this.exchangeRate;
    }
    
    public BigDecimal getInvestmentRatio() {
        return this.investmentRatio;
    }
    
    public BigDecimal getQuantity() {
        return this.quantity;
    }
    
    public BigDecimal getPricePerShare() {
        return this.pricePerShare;
    }
    
    public BigDecimal getTotalAmount() {
        return this.totalAmount;
    }
    
    public String getBuyReason() {
        return this.buyReason;
    }
    
    public String getSellReason() {
        return this.sellReason;
    }
    
    public BigDecimal getSellQuantity() {
        return this.sellQuantity;
    }
    
    public BigDecimal getSellRatio() {
        return this.sellRatio;
    }
    
    public BigDecimal getRealizedProfitRate() {
        return this.realizedProfitRate;
    }
    
    public BigDecimal getSellPrice() {
        return this.sellPrice;
    }
    
    // 명시적인 setter 메서드들
    public void setId(Long id) {
        this.id = id;
    }
    
    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }
    
    public void setStockName(String stockName) {
        this.stockName = stockName;
    }
    
    public void setStockCode(String stockCode) {
        this.stockCode = stockCode;
    }
    
    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }
    
    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }
    
    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }
    
    public void setExchangeRate(BigDecimal exchangeRate) {
        this.exchangeRate = exchangeRate;
    }
    
    public void setInvestmentRatio(BigDecimal investmentRatio) {
        this.investmentRatio = investmentRatio;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public void setPricePerShare(BigDecimal pricePerShare) {
        this.pricePerShare = pricePerShare;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public void setBuyReason(String buyReason) {
        this.buyReason = buyReason;
    }
    
    public void setSellReason(String sellReason) {
        this.sellReason = sellReason;
    }
    
    public void setSellQuantity(BigDecimal sellQuantity) {
        this.sellQuantity = sellQuantity;
    }
    
    public void setSellRatio(BigDecimal sellRatio) {
        this.sellRatio = sellRatio;
    }
    
    public void setRealizedProfitRate(BigDecimal realizedProfitRate) {
        this.realizedProfitRate = realizedProfitRate;
    }
    
    public void setSellPrice(BigDecimal sellPrice) {
        this.sellPrice = sellPrice;
    }
}
