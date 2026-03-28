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
@Table(name = "exchange_rates",
    uniqueConstraints = @UniqueConstraint(columnNames = {"currency_pair", "rate_date"}),
    indexes = {
        @Index(name = "idx_currency_pair", columnList = "currency_pair"),
        @Index(name = "idx_rate_date", columnList = "rate_date"),
        @Index(name = "idx_base_currency", columnList = "base_currency")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String currencyPair; // 통화쌍 (예: USDKRW, EURKRW)
    
    @Column(nullable = false, length = 3)
    private String baseCurrency; // 기준 통화 (예: USD)
    
    @Column(nullable = false, length = 3)
    private String quoteCurrency; // 상대 통화 (예: KRW)
    
    @Column(nullable = false)
    private LocalDate rateDate; // 환율 날짜
    
    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal rate; // 환율
    
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column
    private LocalDateTime updatedAt;
}

