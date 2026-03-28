package com.investmentdiary.service;

import com.investmentdiary.entity.User;
import com.investmentdiary.entity.UserPasswordHistory;
import com.investmentdiary.exception.CustomAuthenticationException;
import com.investmentdiary.repository.UserPasswordHistoryRepository;
import com.investmentdiary.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPasswordHistoryRepository userPasswordHistoryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private PasswordPolicyService passwordPolicyService;

    @BeforeEach
    void setUp() {
        passwordPolicyService = new PasswordPolicyService(userRepository, userPasswordHistoryRepository, passwordEncoder);
    }

    @Test
    void isPasswordChangeRecommended_falseBeforeSixMonths() {
        User user = new User();
        user.setPassword("hash");
        user.setPasswordChangedAt(LocalDateTime.now().minusMonths(5));
        user.setCreatedAt(LocalDateTime.now().minusYears(1));
        assertThat(passwordPolicyService.isPasswordChangeRecommended(user)).isFalse();
    }

    @Test
    void isPasswordChangeRecommended_trueAfterSixMonthsWithoutDefer() {
        User user = new User();
        user.setPassword("hash");
        user.setPasswordChangedAt(LocalDateTime.now().minusMonths(7));
        user.setCreatedAt(LocalDateTime.now().minusYears(1));
        assertThat(passwordPolicyService.isPasswordChangeRecommended(user)).isTrue();
    }

    @Test
    void isPasswordChangeRecommended_falseWhileDeferred() {
        User user = new User();
        user.setPassword("hash");
        user.setPasswordChangedAt(LocalDateTime.now().minusMonths(7));
        user.setPasswordChangeDeferredUntil(LocalDateTime.now().plusMonths(1));
        assertThat(passwordPolicyService.isPasswordChangeRecommended(user)).isFalse();
    }

    @Test
    void validateNewPasswordNotReusingLastThree_rejectsWhenMatchesHistory() {
        User user = new User();
        user.setId(1L);
        user.setPassword("currentHash");
        when(userPasswordHistoryRepository.findTop2ByUser_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of(
                UserPasswordHistory.builder().passwordHash("h1").build(),
                UserPasswordHistory.builder().passwordHash("h2").build()
        ));
        when(passwordEncoder.matches("newpass", "currentHash")).thenReturn(false);
        when(passwordEncoder.matches("newpass", "h1")).thenReturn(true);

        assertThatThrownBy(() -> passwordPolicyService.validateNewPasswordNotReusingLastThree(user, "newpass"))
                .isInstanceOf(CustomAuthenticationException.class);
    }

    @Test
    void deferPasswordChange_setsDeferredUntil() {
        User user = new User();
        user.setId(10L);
        user.setPassword("hash");
        user.setPasswordChangedAt(LocalDateTime.now().minusMonths(7));
        user.setPasswordChangeDeferredUntil(null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        passwordPolicyService.deferPasswordChange(10L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordChangeDeferredUntil()).isNotNull();
        assertThat(captor.getValue().getPasswordChangeDeferredUntil()).isAfter(LocalDateTime.now());
    }

    @Test
    void deferPasswordChange_rejectsWhenNotRecommended() {
        User user = new User();
        user.setId(10L);
        user.setPassword("hash");
        user.setPasswordChangedAt(LocalDateTime.now().minusMonths(1));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordPolicyService.deferPasswordChange(10L))
                .isInstanceOf(CustomAuthenticationException.class);

        verify(userRepository, never()).save(any());
    }
}
