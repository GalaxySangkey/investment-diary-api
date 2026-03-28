package com.investmentdiary.repository;

import com.investmentdiary.entity.PortfolioSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioSettingsRepository extends JpaRepository<PortfolioSettings, Long> {
    
    Optional<PortfolioSettings> findByUserId(Long userId);
    
    boolean existsByUserId(Long userId);
} 