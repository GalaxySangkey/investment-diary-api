package com.investmentdiary.service;

import com.investmentdiary.entity.PortfolioSettings;
import com.investmentdiary.entity.User;
import com.investmentdiary.repository.PortfolioSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PortfolioSettingsService {
    
    private final PortfolioSettingsRepository portfolioSettingsRepository;
    
    /**
     * 기본 포트폴리오 설정 생성.
     * 회원가입 직후에는 {@link com.investmentdiary.event.UserRegisteredPortfolioListener}에서
     * 커밋 이후 호출한다(REQUIRES_NEW + 미커밋 user 행은 교착/장시간 대기 유발).
     */
    @Transactional
    public PortfolioSettings createDefaultPortfolioSettings(User user) {
        log.info("사용자 {}의 기본 포트폴리오 설정 생성", user.getId());
        
        // 이미 존재하는지 다시 확인 (동시성 문제 방지)
        Optional<PortfolioSettings> existing = portfolioSettingsRepository.findByUserId(user.getId());
        if (existing.isPresent()) {
            log.info("포트폴리오 설정이 이미 존재함: userId={}, settingsId={}", user.getId(), existing.get().getId());
            return existing.get();
        }
        
        try {
            PortfolioSettings defaultSettings = PortfolioSettings.builder()
                .user(user)
                .totalSeed(BigDecimal.valueOf(10000000)) // 기본값: 1천만원
                .currency("KRW")
                .build();
            
            PortfolioSettings savedSettings = portfolioSettingsRepository.save(defaultSettings);
            log.info("기본 포트폴리오 설정 생성 완료: userId={}, settingsId={}", user.getId(), savedSettings.getId());
            
            return savedSettings;
        } catch (Exception e) {
            log.error("기본 포트폴리오 설정 생성 실패: userId={}, error={}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("포트폴리오 설정 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }
}

