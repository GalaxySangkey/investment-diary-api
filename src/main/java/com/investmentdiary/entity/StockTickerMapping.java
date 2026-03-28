package com.investmentdiary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_ticker_mapping",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "stock_code"),
        @UniqueConstraint(columnNames = "ticker")
    },
    indexes = {
        @Index(name = "idx_stock_name", columnList = "stock_name"),
        @Index(name = "idx_market", columnList = "market"),
        @Index(name = "idx_country", columnList = "country")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTickerMapping {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 20, unique = true)
    private String stockCode; // 종목코드
    
    @Column(nullable = false, length = 100)
    private String stockName; // 종목명
    
    @Column(nullable = false, length = 50, unique = true)
    private String ticker; // Yahoo Finance 티커
    
    @Column(length = 50)
    private String market; // 시장 (KOSPI, KOSDAQ, NASDAQ, NYSE 등)
    
    @Column(length = 10)
    @Builder.Default
    private String country = "KR"; // 국가 코드 (KR, US, JP 등)
    
    @Column
    @Builder.Default
    private Boolean isActive = true; // 활성화 여부
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;
}

