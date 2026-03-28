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
@Table(name = "monthly_actual_balances", indexes = {
    @Index(name = "idx_user_year_month", columnList = "user_id, year, month"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyActualBalance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "사용자 정보는 필수입니다")
    @JsonIgnore // 순환 참조 방지
    private User user;
    
    @Column(nullable = false)
    @NotNull(message = "연도는 필수입니다")
    @Min(value = 2000, message = "연도는 2000년 이상이어야 합니다")
    @Max(value = 9999, message = "연도는 9999년 이하여야 합니다")
    private Integer year;
    
    @Column(nullable = false)
    @NotNull(message = "월은 필수입니다")
    @Min(value = 1, message = "월은 1월 이상이어야 합니다")
    @Max(value = 12, message = "월은 12월 이하여야 합니다")
    private Integer month;
    
    @Column(name = "actual_balance", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "실제 금액은 0원 이상이어야 합니다")
    private BigDecimal actualBalance;
    
    @Column(name = "difference", precision = 15, scale = 2)
    private BigDecimal difference; // 계산된 금액과 실제 금액의 차이
    
    @Column(name = "investment_seed_addition", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "투자시드 증액은 0원 이상이어야 합니다")
    private BigDecimal investmentSeedAddition; // 해당 월 투자시드 증액 (순수 현금에서 차감, 총 시드에 가산)
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

