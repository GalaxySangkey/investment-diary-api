package com.investmentdiary.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.investmentdiary.util.SensitiveTextAttributeConverter;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "investment_records", indexes = {
    @Index(name = "idx_user_date", columnList = "user_id, record_date"),
    @Index(name = "idx_stock_code", columnList = "stock_code"),
    @Index(name = "idx_type", columnList = "type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvestmentRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "사용자 정보는 필수입니다")
    @JsonIgnore // 순환 참조 방지
    private User user;
    
    @Column(name = "record_date", nullable = false)
    @NotNull(message = "투자 날짜는 필수입니다")
    private LocalDate recordDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @NotNull(message = "투자 유형은 필수입니다")
    private InvestmentType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    @NotNull(message = "자산 유형은 필수입니다")
    private AssetType assetType = AssetType.STOCK; // 기본값: 주식
    
    @Column(name = "stock_name", nullable = false, length = 100)
    @NotBlank(message = "종목명은 필수입니다")
    @Size(max = 100, message = "종목명은 100자 이하여야 합니다")
    private String stockName;
    
    @Column(name = "stock_code", length = 20)
    @Size(max = 20, message = "종목코드는 20자 이하여야 합니다")
    private String stockCode;
    
    // 외환거래 필드
    @Column(name = "currency_pair", length = 20)
    @Size(max = 20, message = "통화쌍은 20자 이하여야 합니다")
    private String currencyPair; // 예: USD/KRW, EUR/USD
    
    @Column(name = "base_currency", length = 3)
    @Size(max = 3, message = "기준 통화는 3자리여야 합니다")
    private String baseCurrency; // 예: USD
    
    @Column(name = "quote_currency", length = 3)
    @Size(max = 3, message = "상대 통화는 3자리여야 합니다")
    private String quoteCurrency; // 예: KRW
    
    @Column(name = "exchange_rate", precision = 12, scale = 4)
    @Positive(message = "환율은 양수여야 합니다")
    private BigDecimal exchangeRate; // 환율
    
    @Column(name = "investment_ratio", nullable = false, precision = 5, scale = 2)
    @NotNull(message = "투자 비율은 필수입니다")
    @DecimalMin(value = "0.00", message = "투자 비율은 0% 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "투자 비율은 100% 이하여야 합니다")
    private BigDecimal investmentRatio;
    
    @Column(precision = 15, scale = 4)
    @DecimalMin(value = "0.0001", message = "수량은 0.0001 이상이어야 합니다")
    private BigDecimal quantity;
    
    @Column(name = "price_per_share", precision = 12, scale = 2)
    @Positive(message = "주당 가격은 양수여야 합니다")
    private BigDecimal pricePerShare;
    
    @Column(name = "total_amount", precision = 15, scale = 2)
    @PositiveOrZero(message = "총 투자 금액은 0 이상이어야 합니다")
    private BigDecimal totalAmount;
    
    @Column(name = "dividend_per_share", precision = 8, scale = 2)
    @PositiveOrZero(message = "주당 배당금은 0 이상이어야 합니다")
    private BigDecimal dividendPerShare;
    
    @Column(name = "dividend_date")
    private LocalDate dividendDate; // 배당 지급일
    
    @Column(name = "dividend_ratio", precision = 5, scale = 2)
    @DecimalMin(value = "0.00", message = "배당률은 0% 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "배당률은 100% 이하여야 합니다")
    private BigDecimal dividendRatio;
    
    @Column(name = "buy_reason", columnDefinition = "TEXT")
    @Size(max = 1000, message = "매수 이유는 1000자 이하여야 합니다")
    @Convert(converter = SensitiveTextAttributeConverter.class)
    private String buyReason;
    
    @Column(name = "sell_reason", columnDefinition = "TEXT")
    @Size(max = 1000, message = "매도 이유는 1000자 이하여야 합니다")
    @Convert(converter = SensitiveTextAttributeConverter.class)
    private String sellReason;
    
    @Column(name = "sell_quantity", precision = 15, scale = 4)
    @DecimalMin(value = "0.0001", message = "매도 수량은 0.0001 이상이어야 합니다")
    private BigDecimal sellQuantity;
    
    @Column(name = "sell_ratio", precision = 5, scale = 2)
    @DecimalMin(value = "0.01", message = "매도 비율은 0.01% 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "매도 비율은 100% 이하여야 합니다")
    private BigDecimal sellRatio;
    
    @Column(name = "sell_price", precision = 15, scale = 2)
    @DecimalMin(value = "0.0", message = "매도 단가는 0 이상이어야 합니다")
    private BigDecimal sellPrice; // 매도 단가 (주당 가격)
    
    @Column(name = "realized_profit_rate", precision = 8, scale = 2)
    @DecimalMin(value = "-100.00", message = "실현 손익률은 -100% 이상이어야 합니다")
    @DecimalMax(value = "1000.00", message = "실현 손익률은 1000% 이하여야 합니다")
    private BigDecimal realizedProfitRate;
    
    @Column(name = "realized_profit_amount", precision = 15, scale = 2)
    private BigDecimal realizedProfitAmount;
    
    @Column(name = "selected_stock_id")
    private Long selectedStockId;
    
    @Column(name = "current_price", precision = 12, scale = 2)
    @PositiveOrZero(message = "현재 가격은 0 이상이어야 합니다")
    private BigDecimal currentPrice;
    
    @Column(name = "unrealized_profit_rate", precision = 8, scale = 2)
    private BigDecimal unrealizedProfitRate;
    
    @Column(name = "unrealized_profit_amount", precision = 15, scale = 2)
    private BigDecimal unrealizedProfitAmount;
    
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 비즈니스 메서드
    public void calculateTotalAmount() {
        if (this.assetType == AssetType.CURRENCY) {
            // 외환거래: 수량 × 환율 = 총 투자금액 (원화)
            if (this.quantity != null && this.exchangeRate != null) {
                this.totalAmount = this.exchangeRate.multiply(this.quantity);
            }
        } else {
            // 주식: 수량 × 주당 가격 = 총 투자금액
            if (this.quantity != null && this.pricePerShare != null) {
                this.totalAmount = this.pricePerShare.multiply(this.quantity);
            }
        }
    }
    
    public void calculateDividendRatio() {
        if (this.dividendPerShare != null && this.pricePerShare != null && 
            this.pricePerShare.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal calculatedRatio = this.dividendPerShare
                .divide(this.pricePerShare, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            // 배당률은 100%를 초과할 수 없음 (validation 제약 조건 준수)
            // 계산된 값이 100%를 초과하면 100%로 제한
            this.dividendRatio = calculatedRatio.compareTo(BigDecimal.valueOf(100)) > 0 
                ? BigDecimal.valueOf(100) 
                : calculatedRatio;
        }
    }
    
    public void calculateUnrealizedProfit() {
        if (this.currentPrice != null && this.pricePerShare != null && 
            this.quantity != null && this.pricePerShare.compareTo(BigDecimal.ZERO) > 0) {
            
            BigDecimal currentValue = this.currentPrice.multiply(this.quantity);
            BigDecimal originalValue = this.pricePerShare.multiply(this.quantity);
            
            this.unrealizedProfitAmount = currentValue.subtract(originalValue);
            this.unrealizedProfitRate = this.unrealizedProfitAmount
                .divide(originalValue, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
    }
    
    public void softDelete() {
        this.isDeleted = true;
    }
    
    public boolean isBuyRecord() {
        return InvestmentType.BUY.equals(this.type);
    }
    
    public boolean isSellRecord() {
        return InvestmentType.SELL.equals(this.type);
    }
    
    public boolean isActive() {
        return !this.isDeleted;
    }
    
    // 추가 setter 메서드들 (Lombok @Setter가 있지만 명시적으로 추가)
    public void setInvestmentRatio(BigDecimal investmentRatio) {
        this.investmentRatio = investmentRatio;
    }
    
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
    
    public void setPricePerShare(BigDecimal pricePerShare) {
        this.pricePerShare = pricePerShare;
    }
    
    public void setBuyReason(String buyReason) {
        this.buyReason = buyReason;
    }
    
    public void setSellReason(String sellReason) {
        this.sellReason = sellReason;
    }
    
    public void setRealizedProfitAmount(BigDecimal realizedProfitAmount) {
        this.realizedProfitAmount = realizedProfitAmount;
    }
    
    public void setRealizedProfitRate(BigDecimal realizedProfitRate) {
        this.realizedProfitRate = realizedProfitRate;
    }
    
    public void setUser(User user) {
        this.user = user;
    }
    
    // 명시적인 getter 메서드들
    public Long getId() { return this.id; }
    public LocalDate getRecordDate() { return this.recordDate; }
    public InvestmentType getType() { return this.type; }
    public AssetType getAssetType() { return this.assetType; }
    public String getStockName() { return this.stockName; }
    public String getStockCode() { return this.stockCode; }
    public String getCurrencyPair() { return this.currencyPair; }
    public String getBaseCurrency() { return this.baseCurrency; }
    public String getQuoteCurrency() { return this.quoteCurrency; }
    public BigDecimal getExchangeRate() { return this.exchangeRate; }
    public BigDecimal getInvestmentRatio() { return this.investmentRatio; }
    public BigDecimal getQuantity() { return this.quantity; }
    public BigDecimal getPricePerShare() { return this.pricePerShare; }
    public BigDecimal getTotalAmount() { return this.totalAmount; }
    public BigDecimal getDividendPerShare() { return this.dividendPerShare; }
    public BigDecimal getDividendRatio() { return this.dividendRatio; }
    public String getBuyReason() { return this.buyReason; }
    public String getSellReason() { return this.sellReason; }
    public BigDecimal getCurrentPrice() { return this.currentPrice; }
    public BigDecimal getUnrealizedProfitRate() { return this.unrealizedProfitRate; }
    public BigDecimal getUnrealizedProfitAmount() { return this.unrealizedProfitAmount; }
    public BigDecimal getRealizedProfitRate() { return this.realizedProfitRate; }
    public BigDecimal getRealizedProfitAmount() { return this.realizedProfitAmount; }
    public BigDecimal getSellQuantity() { return this.sellQuantity; }
    public BigDecimal getSellRatio() { return this.sellRatio; }
    public User getUser() { return this.user; }
    public LocalDateTime getCreatedAt() { return this.createdAt; }
    public LocalDateTime getUpdatedAt() { return this.updatedAt; }
    
    // 명시적인 builder() 메서드 추가
    public static Builder builder() {
        return new Builder();
    }
    
    // 명시적인 Builder 클래스
    public static class Builder {
        private Long id;
        private User user;
        private LocalDate recordDate;
        private InvestmentType type;
        private AssetType assetType = AssetType.STOCK;
        private String stockName;
        private String stockCode;
        private String currencyPair;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal exchangeRate;
        private BigDecimal investmentRatio;
        private BigDecimal quantity;
        private BigDecimal pricePerShare;
        private BigDecimal totalAmount;
        private BigDecimal dividendPerShare;
        private LocalDate dividendDate;
        private BigDecimal dividendRatio;
        private String buyReason;
        private String sellReason;
        private BigDecimal sellQuantity;
        private BigDecimal sellRatio;
        private BigDecimal sellPrice;
        private BigDecimal realizedProfitRate;
        private BigDecimal realizedProfitAmount;
        private Long selectedStockId;
        private BigDecimal currentPrice;
        private BigDecimal unrealizedProfitRate;
        private BigDecimal unrealizedProfitAmount;
        private Boolean isDeleted = false;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public Builder id(Long id) { this.id = id; return this; }
        public Builder user(User user) { this.user = user; return this; }
        public Builder recordDate(LocalDate recordDate) { this.recordDate = recordDate; return this; }
        public Builder type(InvestmentType type) { this.type = type; return this; }
        public Builder assetType(AssetType assetType) { this.assetType = assetType; return this; }
        public Builder stockName(String stockName) { this.stockName = stockName; return this; }
        public Builder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public Builder currencyPair(String currencyPair) { this.currencyPair = currencyPair; return this; }
        public Builder baseCurrency(String baseCurrency) { this.baseCurrency = baseCurrency; return this; }
        public Builder quoteCurrency(String quoteCurrency) { this.quoteCurrency = quoteCurrency; return this; }
        public Builder exchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; return this; }
        public Builder investmentRatio(BigDecimal investmentRatio) { this.investmentRatio = investmentRatio; return this; }
        public Builder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public Builder pricePerShare(BigDecimal pricePerShare) { this.pricePerShare = pricePerShare; return this; }
        public Builder totalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; return this; }
        public Builder dividendPerShare(BigDecimal dividendPerShare) { this.dividendPerShare = dividendPerShare; return this; }
        public Builder dividendDate(LocalDate dividendDate) { this.dividendDate = dividendDate; return this; }
        public Builder dividendRatio(BigDecimal dividendRatio) { this.dividendRatio = dividendRatio; return this; }
        public Builder buyReason(String buyReason) { this.buyReason = buyReason; return this; }
        public Builder sellReason(String sellReason) { this.sellReason = sellReason; return this; }
        public Builder sellQuantity(BigDecimal sellQuantity) { this.sellQuantity = sellQuantity; return this; }
        public Builder sellRatio(BigDecimal sellRatio) { this.sellRatio = sellRatio; return this; }
        public Builder sellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; return this; }
        public Builder realizedProfitRate(BigDecimal realizedProfitRate) { this.realizedProfitRate = realizedProfitRate; return this; }
        public Builder realizedProfitAmount(BigDecimal realizedProfitAmount) { this.realizedProfitAmount = realizedProfitAmount; return this; }
        public Builder selectedStockId(Long selectedStockId) { this.selectedStockId = selectedStockId; return this; }
        public Builder currentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; return this; }
        public Builder unrealizedProfitRate(BigDecimal unrealizedProfitRate) { this.unrealizedProfitRate = unrealizedProfitRate; return this; }
        public Builder unrealizedProfitAmount(BigDecimal unrealizedProfitAmount) { this.unrealizedProfitAmount = unrealizedProfitAmount; return this; }
        public Builder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        
        public InvestmentRecord build() {
            InvestmentRecord record = new InvestmentRecord();
            record.id = this.id;
            record.user = this.user;
            record.recordDate = this.recordDate;
            record.type = this.type;
            record.assetType = this.assetType;
            record.stockName = this.stockName;
            record.stockCode = this.stockCode;
            record.currencyPair = this.currencyPair;
            record.baseCurrency = this.baseCurrency;
            record.quoteCurrency = this.quoteCurrency;
            record.exchangeRate = this.exchangeRate;
            record.investmentRatio = this.investmentRatio;
            record.quantity = this.quantity;
            record.pricePerShare = this.pricePerShare;
            record.totalAmount = this.totalAmount;
            record.dividendPerShare = this.dividendPerShare;
            record.dividendRatio = this.dividendRatio;
            record.buyReason = this.buyReason;
            record.sellReason = this.sellReason;
            record.sellQuantity = this.sellQuantity;
            record.sellRatio = this.sellRatio;
            record.sellPrice = this.sellPrice;
            record.realizedProfitRate = this.realizedProfitRate;
            record.realizedProfitAmount = this.realizedProfitAmount;
            record.selectedStockId = this.selectedStockId;
            record.currentPrice = this.currentPrice;
            record.unrealizedProfitRate = this.unrealizedProfitRate;
            record.unrealizedProfitAmount = this.unrealizedProfitAmount;
            record.isDeleted = this.isDeleted;
            record.createdAt = this.createdAt;
            record.updatedAt = this.updatedAt;
            return record;
        }
    }
    
    public enum InvestmentType {
        BUY, SELL
    }
} 