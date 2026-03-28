package com.investmentdiary.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NotNull(message = "사용자 정보는 필수입니다")
    @JsonIgnore // 순환 참조 방지
    private User user;
    
    @Column(name = "total_seed", nullable = false, precision = 15, scale = 2)
    @NotNull(message = "총 시드머니는 필수입니다")
    @DecimalMin(value = "0.01", message = "총 시드머니는 0.01원 이상이어야 합니다")
    private BigDecimal totalSeed;
    
    @Column(length = 3, nullable = false)
    @NotBlank(message = "통화는 필수입니다")
    @Size(min = 3, max = 3, message = "통화는 3자리여야 합니다")
    @Builder.Default
    private String currency = "KRW";
    
    @Column(name = "risk_tolerance", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RiskTolerance riskTolerance = RiskTolerance.MODERATE;
    
    @Column(name = "investment_goal", length = 100)
    @Size(max = 100, message = "투자 목표는 100자 이하여야 합니다")
    private String investmentGoal;
    
    @Column(name = "target_profit_rate", precision = 5, scale = 2)
    @DecimalMin(value = "-100.00", message = "목표 수익률은 -100% 이상이어야 합니다")
    @DecimalMax(value = "1000.00", message = "목표 수익률은 1000% 이하여야 합니다")
    private BigDecimal targetProfitRate;
    
    @Column(name = "max_single_stock_ratio", precision = 5, scale = 2)
    @DecimalMin(value = "1.00", message = "단일 종목 최대 비율은 1% 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "단일 종목 최대 비율은 100% 이하여야 합니다")
    @Builder.Default
    private BigDecimal maxSingleStockRatio = BigDecimal.valueOf(20.00);
    
    @Column(name = "max_sector_ratio", precision = 5, scale = 2)
    @DecimalMin(value = "1.00", message = "섹터 최대 비율은 1% 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "섹터 최대 비율은 100% 이하여야 합니다")
    @Builder.Default
    private BigDecimal maxSectorRatio = BigDecimal.valueOf(40.00);
    
    @Column(name = "rebalancing_frequency", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RebalancingFrequency rebalancingFrequency = RebalancingFrequency.MONTHLY;
    
    @Column(name = "last_rebalancing_date")
    private LocalDateTime lastRebalancingDate;
    
    @Column(name = "auto_rebalancing_enabled")
    @Builder.Default
    private Boolean autoRebalancingEnabled = false;
    
    @Column(name = "dividend_reinvestment_enabled")
    @Builder.Default
    private Boolean dividendReinvestmentEnabled = true;
    
    @Column(name = "tax_optimization_enabled")
    @Builder.Default
    private Boolean taxOptimizationEnabled = false;
    
    @Column(name = "notification_enabled")
    @Builder.Default
    private Boolean notificationEnabled = true;
    
    @Column(name = "email_notification_enabled")
    @Builder.Default
    private Boolean emailNotificationEnabled = true;
    
    @Column(name = "sms_notification_enabled")
    @Builder.Default
    private Boolean smsNotificationEnabled = false;
    
    @Column(name = "push_notification_enabled")
    @Builder.Default
    private Boolean pushNotificationEnabled = true;
    
    @Column(name = "profit_alert_threshold", precision = 5, scale = 2)
    @DecimalMin(value = "1.00", message = "수익 알림 임계값은 1% 이상이어야 합니다")
    @DecimalMax(value = "100.00", message = "수익 알림 임계값은 100% 이하여야 합니다")
    @Builder.Default
    private BigDecimal profitAlertThreshold = BigDecimal.valueOf(10.00);
    
    @Column(name = "loss_alert_threshold", precision = 5, scale = 2)
    @DecimalMin(value = "-100.00", message = "손실 알림 임계값은 -100% 이상이어야 합니다")
    @DecimalMax(value = "0.00", message = "손실 알림 임계값은 0% 이하여야 합니다")
    @Builder.Default
    private BigDecimal lossAlertThreshold = BigDecimal.valueOf(-10.00);
    
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 비즈니스 메서드
    public void updateLastRebalancingDate() {
        this.lastRebalancingDate = LocalDateTime.now();
    }
    
    public boolean needsRebalancing() {
        if (!this.autoRebalancingEnabled || this.lastRebalancingDate == null) {
            return false;
        }
        
        LocalDateTime nextRebalancingDate = switch (this.rebalancingFrequency) {
            case DAILY -> this.lastRebalancingDate.plusDays(1);
            case WEEKLY -> this.lastRebalancingDate.plusWeeks(1);
            case MONTHLY -> this.lastRebalancingDate.plusMonths(1);
            case QUARTERLY -> this.lastRebalancingDate.plusMonths(3);
            case YEARLY -> this.lastRebalancingDate.plusYears(1);
        };
        
        return LocalDateTime.now().isAfter(nextRebalancingDate);
    }
    
    public boolean isOverSingleStockLimit(BigDecimal currentRatio) {
        return currentRatio.compareTo(this.maxSingleStockRatio) > 0;
    }
    
    public boolean isOverSectorLimit(BigDecimal currentRatio) {
        return currentRatio.compareTo(this.maxSectorRatio) > 0;
    }
    
    public void softDelete() {
        this.isDeleted = true;
    }
    
    public boolean isActive() {
        return !this.isDeleted;
    }
    
    // 추가 setter 메서드들 (Lombok @Setter가 있지만 명시적으로 추가)
    public void setTotalSeed(BigDecimal totalSeed) {
        this.totalSeed = totalSeed;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public void setRiskTolerance(RiskTolerance riskTolerance) {
        this.riskTolerance = riskTolerance;
    }
    
    public void setInvestmentGoal(String investmentGoal) {
        this.investmentGoal = investmentGoal;
    }
    
    public void setTargetProfitRate(BigDecimal targetProfitRate) {
        this.targetProfitRate = targetProfitRate;
    }
    
    public void setMaxSingleStockRatio(BigDecimal maxSingleStockRatio) {
        this.maxSingleStockRatio = maxSingleStockRatio;
    }
    
    public void setMaxSectorRatio(BigDecimal maxSectorRatio) {
        this.maxSectorRatio = maxSectorRatio;
    }
    
    public void setRebalancingFrequency(RebalancingFrequency rebalancingFrequency) {
        this.rebalancingFrequency = rebalancingFrequency;
    }
    
    public void setAutoRebalancingEnabled(Boolean autoRebalancingEnabled) {
        this.autoRebalancingEnabled = autoRebalancingEnabled;
    }
    
    public void setDividendReinvestmentEnabled(Boolean dividendReinvestmentEnabled) {
        this.dividendReinvestmentEnabled = dividendReinvestmentEnabled;
    }
    
    public void setTaxOptimizationEnabled(Boolean taxOptimizationEnabled) {
        this.taxOptimizationEnabled = taxOptimizationEnabled;
    }
    
    public void setNotificationEnabled(Boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }
    
    public void setProfitAlertThreshold(BigDecimal profitAlertThreshold) {
        this.profitAlertThreshold = profitAlertThreshold;
    }
    
    public void setLossAlertThreshold(BigDecimal lossAlertThreshold) {
        this.lossAlertThreshold = lossAlertThreshold;
    }
    
    // 명시적인 getter 메서드들
    public Long getId() { return this.id; }
    public BigDecimal getTotalSeed() { return this.totalSeed; }
    public String getCurrency() { return this.currency; }
    public RiskTolerance getRiskTolerance() { return this.riskTolerance; }
    public String getInvestmentGoal() { return this.investmentGoal; }
    public BigDecimal getTargetProfitRate() { return this.targetProfitRate; }
    public BigDecimal getMaxSingleStockRatio() { return this.maxSingleStockRatio; }
    public BigDecimal getMaxSectorRatio() { return this.maxSectorRatio; }
    public RebalancingFrequency getRebalancingFrequency() { return this.rebalancingFrequency; }
    public Boolean getAutoRebalancingEnabled() { return this.autoRebalancingEnabled; }
    public Boolean getDividendReinvestmentEnabled() { return this.dividendReinvestmentEnabled; }
    public Boolean getTaxOptimizationEnabled() { return this.taxOptimizationEnabled; }
    public Boolean getNotificationEnabled() { return this.notificationEnabled; }
    public BigDecimal getProfitAlertThreshold() { return this.profitAlertThreshold; }
    public BigDecimal getLossAlertThreshold() { return this.lossAlertThreshold; }
    public LocalDateTime getLastRebalancingDate() { return this.lastRebalancingDate; }
    public LocalDateTime getCreatedAt() { return this.createdAt; }
    public LocalDateTime getUpdatedAt() { return this.updatedAt; }
    
    public enum RiskTolerance {
        CONSERVATIVE, MODERATE, AGGRESSIVE
    }
    
    public enum RebalancingFrequency {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY
    }
} 