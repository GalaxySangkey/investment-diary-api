package com.investmentdiary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_dividends",
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "dividend_date"}),
    indexes = {
        @Index(name = "idx_ticker", columnList = "ticker"),
        @Index(name = "idx_stock_code", columnList = "stock_code"),
        @Index(name = "idx_dividend_date", columnList = "dividend_date")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDividend {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String ticker; // Yahoo Finance 티커
    
    @Column(length = 20)
    private String stockCode; // 종목코드
    
    @Column(nullable = false, length = 100)
    private String stockName; // 종목명
    
    @Column(nullable = false)
    private LocalDate dividendDate; // 배당 지급일
    
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal dividendPerShare; // 주당 배당금
    
    @Column(precision = 8, scale = 4)
    private BigDecimal dividendYield; // 배당수익률 (%)
    
    @Column(precision = 15, scale = 4)
    private BigDecimal annualDividend; // 연간 배당금
    
    @Column(length = 3)
    @Builder.Default
    private String currency = "KRW"; // 통화
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;
}

