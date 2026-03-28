package com.investmentdiary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "stock_listings",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stock_code", "country"})
    },
    indexes = {
        @Index(name = "idx_stock_name", columnList = "stock_name"),
        @Index(name = "idx_market", columnList = "market"),
        @Index(name = "idx_country", columnList = "country"),
        @Index(name = "idx_is_active", columnList = "is_active"),
        @Index(name = "idx_stock_code", columnList = "stock_code")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockListing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(nullable = false, length = 20)
    private String market; // KOSPI, KOSDAQ, KONEX, NASDAQ, NYSE

    @Column(length = 50)
    private String ticker; // Yahoo Finance 티커

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String country = "KR";

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;
}
