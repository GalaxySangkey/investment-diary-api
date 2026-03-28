package com.investmentdiary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_levels")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLevel {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer experience = 0;
    
    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Integer totalPoints = 0;
    
    @Column(name = "current_points", nullable = false)
    @Builder.Default
    private Integer currentPoints = 0;
    
    @Column(name = "level_up_date")
    private LocalDateTime levelUpDate;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // 비즈니스 메서드
    public void addExperience(int exp) {
        this.experience += exp;
        checkLevelUp();
    }
    
    public void addPoints(int points) {
        this.totalPoints += points;
        this.currentPoints += points;
    }
    
    public boolean usePoints(int points) {
        if (this.currentPoints >= points) {
            this.currentPoints -= points;
            return true;
        }
        return false;
    }
    
    private void checkLevelUp() {
        int requiredExp = calculateRequiredExperience(this.level + 1);
        if (this.experience >= requiredExp) {
            this.level++;
            this.levelUpDate = LocalDateTime.now();
        }
    }
    
    private int calculateRequiredExperience(int level) {
        // 레벨업에 필요한 경험치 계산 (예: 레벨^2 * 100)
        return level * level * 100;
    }
    
    public int getExperienceToNextLevel() {
        int requiredExp = calculateRequiredExperience(this.level + 1);
        return Math.max(0, requiredExp - this.experience);
    }
    
    public double getLevelProgress() {
        int currentLevelExp = calculateRequiredExperience(this.level);
        int nextLevelExp = calculateRequiredExperience(this.level + 1);
        int expInCurrentLevel = this.experience - currentLevelExp;
        int expNeededForLevel = nextLevelExp - currentLevelExp;
        
        return (double) expInCurrentLevel / expNeededForLevel;
    }
} 