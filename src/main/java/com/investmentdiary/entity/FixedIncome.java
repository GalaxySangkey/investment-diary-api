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
@Table(name = "fixed_incomes", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedIncome {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "사용자 정보는 필수입니다")
    @JsonIgnore // 순환 참조 방지
    private User user;
    
    @Column(nullable = false, length = 100)
    @NotBlank(message = "수입 항목명은 필수입니다")
    @Size(max = 100, message = "수입 항목명은 100자 이하여야 합니다")
    @Convert(converter = SensitiveTextAttributeConverter.class)
    private String name;
    
    @Column(nullable = false, precision = 15, scale = 2)
    @NotNull(message = "금액은 필수입니다")
    @DecimalMin(value = "0.01", message = "금액은 0.01원 이상이어야 합니다")
    private BigDecimal amount;
    
    @Column(nullable = false)
    @NotNull(message = "일자는 필수입니다")
    @Min(value = 1, message = "일자는 1일 이상이어야 합니다")
    @Max(value = 31, message = "일자는 31일 이하여야 합니다")
    private Integer day; // 매월 며칠에 받는지
    
    @Column(name = "start_date")
    private LocalDate startDate; // 시작 날짜 (null이면 계산 시작 날짜부터)
    
    @Column(name = "end_date")
    private LocalDate endDate; // 종료 날짜 (null이면 무제한)
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

