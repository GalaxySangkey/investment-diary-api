package com.investmentdiary.service;

import com.investmentdiary.dto.auth.LoginResponse;
import com.investmentdiary.entity.User;
import com.investmentdiary.entity.UserPasswordHistory;
import com.investmentdiary.exception.CustomAuthenticationException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.repository.UserPasswordHistoryRepository;
import com.investmentdiary.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PasswordPolicyService {

    static final int PASSWORD_CHANGE_RECOMMEND_AFTER_MONTHS = 6;
    static final int PASSWORD_CHANGE_DEFER_MONTHS = 3;

    private final UserRepository userRepository;
    private final UserPasswordHistoryRepository userPasswordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordPolicyService(
            UserRepository userRepository,
            UserPasswordHistoryRepository userPasswordHistoryRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userPasswordHistoryRepository = userPasswordHistoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean hasPassword(User user) {
        return user.getPassword() != null && !user.getPassword().isBlank();
    }

    /**
     * 비밀번호를 가진 신규 계정(회원가입/WebAuthn 등)에 변경 기준 시각을 설정합니다.
     */
    @Transactional
    public void markNewPasswordAccount(User user) {
        if (!hasPassword(user)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now);
        user.setPasswordChangeDeferredUntil(null);
        userRepository.save(user);
    }

    public void applyToLoginResponse(LoginResponse.Builder builder, User user) {
        if (!hasPassword(user)) {
            builder.passwordChangeRecommended(false)
                    .passwordChangeDueAt(null)
                    .passwordChangeDeferredUntil(null);
            return;
        }
        LocalDateTime baseline = user.getPasswordChangedAt() != null
                ? user.getPasswordChangedAt()
                : user.getCreatedAt();
        if (baseline == null) {
            baseline = LocalDateTime.now();
        }
        LocalDateTime dueAt = baseline.plusMonths(PASSWORD_CHANGE_RECOMMEND_AFTER_MONTHS);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deferredUntil = user.getPasswordChangeDeferredUntil();
        boolean recommended = !now.isBefore(dueAt)
                && (deferredUntil == null || !now.isBefore(deferredUntil));
        builder.passwordChangeRecommended(recommended)
                .passwordChangeDueAt(dueAt)
                .passwordChangeDeferredUntil(deferredUntil);
    }

    public boolean isPasswordChangeRecommended(User user) {
        if (!hasPassword(user)) {
            return false;
        }
        LocalDateTime baseline = user.getPasswordChangedAt() != null
                ? user.getPasswordChangedAt()
                : user.getCreatedAt();
        if (baseline == null) {
            return false;
        }
        LocalDateTime dueAt = baseline.plusMonths(PASSWORD_CHANGE_RECOMMEND_AFTER_MONTHS);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(dueAt)) {
            return false;
        }
        LocalDateTime deferredUntil = user.getPasswordChangeDeferredUntil();
        return deferredUntil == null || !now.isBefore(deferredUntil);
    }

    @Transactional
    public void deferPasswordChange(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        if (!hasPassword(user)) {
            throw new CustomAuthenticationException("비밀번호가 설정되지 않은 계정입니다.");
        }
        if (!isPasswordChangeRecommended(user)) {
            throw new CustomAuthenticationException("비밀번호 변경 유예를 적용할 수 없습니다.");
        }
        LocalDateTime deferredUntil = user.getPasswordChangeDeferredUntil();
        LocalDateTime now = LocalDateTime.now();
        if (deferredUntil != null && now.isBefore(deferredUntil)) {
            throw new CustomAuthenticationException("이미 비밀번호 변경 유예 기간입니다.");
        }
        user.setPasswordChangeDeferredUntil(now.plusMonths(PASSWORD_CHANGE_DEFER_MONTHS));
        userRepository.save(user);
    }

    public void validateNewPasswordNotReusingLastThree(User user, String newPlainPassword) {
        List<String> hashesToCheck = new ArrayList<>();
        hashesToCheck.add(user.getPassword());
        for (UserPasswordHistory h : userPasswordHistoryRepository.findTop2ByUser_IdOrderByCreatedAtDesc(user.getId())) {
            hashesToCheck.add(h.getPasswordHash());
        }
        for (String hash : hashesToCheck) {
            if (hash != null && passwordEncoder.matches(newPlainPassword, hash)) {
                throw new CustomAuthenticationException("최근에 사용한 비밀번호는 다시 사용할 수 없습니다.");
            }
        }
    }

    @Transactional
    public void recordPasswordRotation(User user, String oldEncodedPassword, String newEncodedPassword) {
        UserPasswordHistory history = UserPasswordHistory.builder()
                .user(user)
                .passwordHash(oldEncodedPassword)
                .createdAt(LocalDateTime.now())
                .build();
        userPasswordHistoryRepository.save(history);
        user.setPassword(newEncodedPassword);
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordChangeDeferredUntil(null);
        userRepository.save(user);
    }
}
