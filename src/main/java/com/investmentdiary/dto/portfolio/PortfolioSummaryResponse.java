package com.investmentdiary.dto.portfolio;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PortfolioSummaryResponse {
    private BigDecimal totalSeed;
    private BigDecimal totalInvestment;
    private BigDecimal totalProfitRate;
    private BigDecimal totalDividendRate;
    private BigDecimal totalProfitAmount;
    private BigDecimal totalDividendAmount;
    private Integer dividendYear; // 배당 기준 연도
    private LocalDateTime lastUpdated;
    
    // 명시적인 builder() 메서드 추가
    public static Builder builder() {
        return new Builder();
    }
    
    // 명시적인 Builder 클래스
    public static class Builder {
        private BigDecimal totalSeed;
        private BigDecimal totalInvestment;
        private BigDecimal totalProfitRate;
        private BigDecimal totalDividendRate;
        private BigDecimal totalProfitAmount;
        private BigDecimal totalDividendAmount;
        private Integer dividendYear;
        private LocalDateTime lastUpdated;
        
        public Builder totalSeed(BigDecimal totalSeed) { this.totalSeed = totalSeed; return this; }
        public Builder totalInvestment(BigDecimal totalInvestment) { this.totalInvestment = totalInvestment; return this; }
        public Builder totalProfitRate(BigDecimal totalProfitRate) { this.totalProfitRate = totalProfitRate; return this; }
        public Builder totalDividendRate(BigDecimal totalDividendRate) { this.totalDividendRate = totalDividendRate; return this; }
        public Builder totalProfitAmount(BigDecimal totalProfitAmount) { this.totalProfitAmount = totalProfitAmount; return this; }
        public Builder totalDividendAmount(BigDecimal totalDividendAmount) { this.totalDividendAmount = totalDividendAmount; return this; }
        public Builder dividendYear(Integer dividendYear) { this.dividendYear = dividendYear; return this; }
        public Builder lastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; return this; }
        
        public PortfolioSummaryResponse build() {
            PortfolioSummaryResponse response = new PortfolioSummaryResponse();
            response.totalSeed = this.totalSeed;
            response.totalInvestment = this.totalInvestment;
            response.totalProfitRate = this.totalProfitRate;
            response.totalDividendRate = this.totalDividendRate;
            response.totalProfitAmount = this.totalProfitAmount;
            response.totalDividendAmount = this.totalDividendAmount;
            response.dividendYear = this.dividendYear;
            response.lastUpdated = this.lastUpdated;
            return response;
        }
    }
} 