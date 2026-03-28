package com.investmentdiary.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.investmentdiary.repository.UserRepository;
import com.investmentdiary.service.PortfolioSettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원가입 직후 같은 요청 안에서 REQUIRES_NEW로 portfolio_settings를 넣으면
 * 아직 커밋되지 않은 users 행 락과 FK 검사가 맞물려 대기/교착이 날 수 있다.
 * 커밋 완료 후 별도 트랜잭션으로 기본 설정을 생성한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredPortfolioListener {

    private final UserRepository userRepository;
    private final PortfolioSettingsService portfolioSettingsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        Long userId = event.userId();
        userRepository.findById(userId).ifPresentOrElse(
            user -> {
                try {
                    portfolioSettingsService.createDefaultPortfolioSettings(user);
                } catch (Exception e) {
                    log.error("커밋 후 기본 포트폴리오 설정 생성 실패: userId={}", userId, e);
                }
            },
            () -> log.warn("UserRegisteredEvent: 사용자 없음 userId={}", userId)
        );
    }
}
