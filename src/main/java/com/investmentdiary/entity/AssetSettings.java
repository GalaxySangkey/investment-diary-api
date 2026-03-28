package com.investmentdiary.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table(name = "asset_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NotNull(message = "사용자 정보는 필수입니다")
    @JsonIgnore // 순환 참조 방지
    private User user;
    
    @Column(name = "start_date", nullable = false)
    @NotNull(message = "계산 시작 날짜는 필수입니다")
    private LocalDate startDate;
    
    @Column(name = "initial_balance", nullable = false, precision = 15, scale = 2)
    @NotNull(message = "시작 금액은 필수입니다")
    @DecimalMin(value = "0.00", message = "시작 금액은 0원 이상이어야 합니다")
    private BigDecimal initialBalance;
    
    @Column(name = "savings", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "월 저축액은 0원 이상이어야 합니다")
    private BigDecimal savings; // 월 저축액
    
    @Column(name = "existing_savings", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "기존 저축액은 0원 이상이어야 합니다")
    private BigDecimal existingSavings; // 기존 저축액 (현재 보유한 저축액)
    
    @Column(name = "retirement_pension", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "퇴직연금은 0원 이상이어야 합니다")
    private BigDecimal retirementPension; // 퇴직연금
    
    @Column(name = "investment_seed", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "투자시드는 0원 이상이어야 합니다")
    private BigDecimal investmentSeed; // 투자시드
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

