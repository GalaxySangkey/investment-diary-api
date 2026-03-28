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
@Table(name = "stock_prices", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"ticker", "price_date"}),
    indexes = {
        @Index(name = "idx_ticker", columnList = "ticker"),
        @Index(name = "idx_stock_code", columnList = "stock_code"),
        @Index(name = "idx_price_date", columnList = "price_date"),
        @Index(name = "idx_stock_name", columnList = "stock_name")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockPrice {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String ticker; // Yahoo Finance 티커 (예: 005930.KS, AAPL)
    
    @Column(length = 20)
    private String stockCode; // 종목코드 (한국 주식의 경우)
    
    @Column(nullable = false, length = 100)
    private String stockName; // 종목명
    
    @Column(nullable = false)
    private LocalDate priceDate; // 가격 날짜
    
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal closePrice; // 종가
    
    @Column(precision = 15, scale = 4)
    private BigDecimal openPrice; // 시가
    
    @Column(precision = 15, scale = 4)
    private BigDecimal highPrice; // 고가
    
    @Column(precision = 15, scale = 4)
    private BigDecimal lowPrice; // 저가
    
    @Column
    private Long volume; // 거래량
    
    @Column(length = 3)
    @Builder.Default
    private String currency = "KRW"; // 통화 (KRW, USD, EUR 등)
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;
}

