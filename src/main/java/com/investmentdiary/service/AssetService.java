package com.investmentdiary.service;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.entity.AssetSettings;
import com.investmentdiary.entity.FixedExpense;
import com.investmentdiary.entity.FixedIncome;
import com.investmentdiary.entity.MonthlyActualBalance;
import com.investmentdiary.entity.PortfolioSettings;
import com.investmentdiary.entity.User;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.repository.AssetSettingsRepository;
import com.investmentdiary.repository.FixedExpenseRepository;
import com.investmentdiary.repository.FixedIncomeRepository;
import com.investmentdiary.repository.MonthlyActualBalanceRepository;
import com.investmentdiary.repository.PortfolioSettingsRepository;
import com.investmentdiary.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {
    
    private final AssetSettingsRepository assetSettingsRepository;
    private final MonthlyActualBalanceRepository monthlyActualBalanceRepository;
    private final FixedIncomeRepository fixedIncomeRepository;
    private final FixedExpenseRepository fixedExpenseRepository;
    private final PortfolioSettingsRepository portfolioSettingsRepository;
    private final UserRepository userRepository;
    
    /**
     * 자산 설정 조회
     */
    public ApiResponse<AssetSettings> getAssetSettings(Long userId) {
        log.info("사용자 {}의 자산 설정 조회", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<AssetSettings> settings = assetSettingsRepository.findByUser(user);
        
        if (settings.isEmpty()) {
            return ApiResponse.success(null, "자산 설정이 없습니다.");
        }
        
        return ApiResponse.success(settings.get(), "자산 설정을 성공적으로 조회했습니다.");
    }
    
    /**
     * 자산 설정 생성 또는 수정
     */
    @Transactional(readOnly = false)
    public ApiResponse<AssetSettings> saveAssetSettings(Long userId, LocalDate startDate, BigDecimal initialBalance, BigDecimal savings, BigDecimal existingSavings, BigDecimal retirementPension, BigDecimal investmentSeed) {
        log.info("사용자 {}의 자산 설정 저장: startDate={}, initialBalance={}, savings={}, existingSavings={}, retirementPension={}, investmentSeed={}", userId, startDate, initialBalance, savings, existingSavings, retirementPension, investmentSeed);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<AssetSettings> existingSettings = assetSettingsRepository.findByUser(user);
        
        AssetSettings settings;
        if (existingSettings.isPresent()) {
            settings = existingSettings.get();
            settings.setStartDate(startDate);
            settings.setInitialBalance(initialBalance);
            // savings는 null이어도 설정 (0도 유효한 값)
            settings.setSavings(savings);
            settings.setExistingSavings(existingSavings);
            settings.setRetirementPension(retirementPension);
            settings.setInvestmentSeed(investmentSeed);
            log.info("자산 설정 업데이트: settingsId={}, savings={}, existingSavings={}, retirementPension={}, investmentSeed={}", settings.getId(), savings, existingSavings, retirementPension, investmentSeed);
        } else {
            settings = AssetSettings.builder()
                .user(user)
                .startDate(startDate)
                .initialBalance(initialBalance)
                .savings(savings)
                .existingSavings(existingSavings)
                .retirementPension(retirementPension)
                .investmentSeed(investmentSeed)
                .build();
            log.info("자산 설정 생성: savings={}, existingSavings={}, retirementPension={}, investmentSeed={}", savings, existingSavings, retirementPension, investmentSeed);
        }
        
        AssetSettings savedSettings = assetSettingsRepository.save(settings);
        
        // 총 시드 = 초기 투자시드 + 월별 투자시드 증액 합계
        recomputeAndUpdatePortfolioTotalSeed(userId);
        
        return ApiResponse.success(savedSettings, "자산 설정을 성공적으로 저장했습니다.");
    }
    
    /**
     * 포트폴리오 총시드 재계산 및 업데이트 (초기 투자시드 + 월별 투자시드 증액 합계)
     */
    private void recomputeAndUpdatePortfolioTotalSeed(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<AssetSettings> settingsOpt = assetSettingsRepository.findByUser(user);
        BigDecimal initialSeed = BigDecimal.ZERO;
        if (settingsOpt.isPresent() && settingsOpt.get().getInvestmentSeed() != null) {
            initialSeed = settingsOpt.get().getInvestmentSeed();
        }
        
        List<MonthlyActualBalance> allMonthly = monthlyActualBalanceRepository.findByUser(user);
        BigDecimal sumAdditions = allMonthly.stream()
            .map(MonthlyActualBalance::getInvestmentSeedAddition)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalSeed = initialSeed.add(sumAdditions);
        
        Optional<PortfolioSettings> portfolioSettingsOpt = portfolioSettingsRepository.findByUserId(userId);
        if (portfolioSettingsOpt.isPresent()) {
            PortfolioSettings portfolioSettings = portfolioSettingsOpt.get();
            portfolioSettings.setTotalSeed(totalSeed);
            portfolioSettingsRepository.save(portfolioSettings);
            log.info("포트폴리오 총시드 재계산 반영: userId={}, totalSeed={} (초기={}, 월별증액합={})", userId, totalSeed, initialSeed, sumAdditions);
        } else {
            PortfolioSettings portfolioSettings = PortfolioSettings.builder()
                .user(user)
                .totalSeed(totalSeed)
                .currency("KRW")
                .build();
            portfolioSettingsRepository.save(portfolioSettings);
            log.info("포트폴리오 설정 생성: userId={}, totalSeed={}", userId, totalSeed);
        }
    }
    
    /**
     * 고정 수입 목록 조회
     */
    public ApiResponse<List<FixedIncome>> getFixedIncomes(Long userId) {
        log.info("사용자 {}의 고정 수입 목록 조회", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        List<FixedIncome> incomes = fixedIncomeRepository.findByUser(user);
        
        return ApiResponse.success(incomes, "고정 수입 목록을 성공적으로 조회했습니다.");
    }
    
    /**
     * 고정 수입 생성
     */
    @Transactional(readOnly = false)
    public ApiResponse<FixedIncome> createFixedIncome(Long userId, String name, BigDecimal amount, Integer day, LocalDate startDate, LocalDate endDate) {
        log.info("사용자 {}의 고정 수입 생성: name={}, amount={}, day={}, startDate={}, endDate={}", userId, name, amount, day, startDate, endDate);
        
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 이후여야 합니다.");
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        FixedIncome income = FixedIncome.builder()
            .user(user)
            .name(name)
            .amount(amount)
            .day(day)
            .startDate(startDate)
            .endDate(endDate)
            .build();
        
        FixedIncome savedIncome = fixedIncomeRepository.save(income);
        return ApiResponse.success(savedIncome, "고정 수입을 성공적으로 생성했습니다.");
    }
    
    /**
     * 고정 수입 수정
     */
    @Transactional(readOnly = false)
    public ApiResponse<FixedIncome> updateFixedIncome(Long userId, Long incomeId, String name, BigDecimal amount, Integer day, LocalDate startDate, LocalDate endDate) {
        log.info("사용자 {}의 고정 수입 수정: incomeId={}, name={}, amount={}, day={}, startDate={}, endDate={}", userId, incomeId, name, amount, day, startDate, endDate);
        
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 이후여야 합니다.");
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        FixedIncome income = fixedIncomeRepository.findById(incomeId)
            .orElseThrow(() -> new IllegalArgumentException("고정 수입을 찾을 수 없습니다."));
        
        if (!income.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("다른 사용자의 고정 수입은 수정할 수 없습니다.");
        }
        
        income.setName(name);
        income.setAmount(amount);
        income.setDay(day);
        income.setStartDate(startDate);
        income.setEndDate(endDate);
        
        FixedIncome savedIncome = fixedIncomeRepository.save(income);
        return ApiResponse.success(savedIncome, "고정 수입을 성공적으로 수정했습니다.");
    }
    
    /**
     * 고정 수입 삭제
     */
    @Transactional(readOnly = false)
    public ApiResponse<Void> deleteFixedIncome(Long userId, Long incomeId) {
        log.info("사용자 {}의 고정 수입 삭제: incomeId={}", userId, incomeId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        FixedIncome income = fixedIncomeRepository.findById(incomeId)
            .orElseThrow(() -> new IllegalArgumentException("고정 수입을 찾을 수 없습니다."));
        
        if (!income.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("다른 사용자의 고정 수입은 삭제할 수 없습니다.");
        }
        
        fixedIncomeRepository.delete(income);
        return ApiResponse.success(null, "고정 수입을 성공적으로 삭제했습니다.");
    }
    
    /**
     * 고정 지출 목록 조회
     */
    public ApiResponse<List<FixedExpense>> getFixedExpenses(Long userId) {
        log.info("사용자 {}의 고정 지출 목록 조회", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        List<FixedExpense> expenses = fixedExpenseRepository.findByUser(user);
        
        return ApiResponse.success(expenses, "고정 지출 목록을 성공적으로 조회했습니다.");
    }
    
    /**
     * 고정 지출 생성
     */
    @Transactional(readOnly = false)
    public ApiResponse<FixedExpense> createFixedExpense(Long userId, String name, BigDecimal amount, Integer day, LocalDate startDate, LocalDate endDate) {
        log.info("사용자 {}의 고정 지출 생성: name={}, amount={}, day={}, startDate={}, endDate={}", userId, name, amount, day, startDate, endDate);
        
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 이후여야 합니다.");
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        FixedExpense expense = FixedExpense.builder()
            .user(user)
            .name(name)
            .amount(amount)
            .day(day)
            .startDate(startDate)
            .endDate(endDate)
            .build();
        
        FixedExpense savedExpense = fixedExpenseRepository.save(expense);
        return ApiResponse.success(savedExpense, "고정 지출을 성공적으로 생성했습니다.");
    }
    
    /**
     * 고정 지출 수정
     */
    @Transactional(readOnly = false)
    public ApiResponse<FixedExpense> updateFixedExpense(Long userId, Long expenseId, String name, BigDecimal amount, Integer day, LocalDate startDate, LocalDate endDate) {
        log.info("사용자 {}의 고정 지출 수정: expenseId={}, name={}, amount={}, day={}, startDate={}, endDate={}", userId, expenseId, name, amount, day, startDate, endDate);
        
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료 날짜는 시작 날짜보다 이후여야 합니다.");
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        FixedExpense expense = fixedExpenseRepository.findById(expenseId)
            .orElseThrow(() -> new IllegalArgumentException("고정 지출을 찾을 수 없습니다."));
        
        if (!expense.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("다른 사용자의 고정 지출은 수정할 수 없습니다.");
        }
        
        expense.setName(name);
        expense.setAmount(amount);
        expense.setDay(day);
        expense.setStartDate(startDate);
        expense.setEndDate(endDate);
        
        FixedExpense savedExpense = fixedExpenseRepository.save(expense);
        return ApiResponse.success(savedExpense, "고정 지출을 성공적으로 수정했습니다.");
    }
    
    /**
     * 고정 지출 삭제
     */
    @Transactional(readOnly = false)
    public ApiResponse<Void> deleteFixedExpense(Long userId, Long expenseId) {
        log.info("사용자 {}의 고정 지출 삭제: expenseId={}", userId, expenseId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        FixedExpense expense = fixedExpenseRepository.findById(expenseId)
            .orElseThrow(() -> new IllegalArgumentException("고정 지출을 찾을 수 없습니다."));
        
        if (!expense.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("다른 사용자의 고정 지출은 삭제할 수 없습니다.");
        }
        
        fixedExpenseRepository.delete(expense);
        return ApiResponse.success(null, "고정 지출을 성공적으로 삭제했습니다.");
    }
    
    /**
     * 월별 실제 금액 조회
     */
    public ApiResponse<MonthlyActualBalance> getMonthlyActualBalance(Long userId, Integer year, Integer month) {
        log.info("사용자 {}의 {}년 {}월 실제 금액 조회", userId, year, month);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<MonthlyActualBalance> balance = monthlyActualBalanceRepository.findByUserAndYearAndMonth(user, year, month);
        
        if (balance.isEmpty()) {
            return ApiResponse.success(null, "해당 월의 실제 금액 기록이 없습니다.");
        }
        
        return ApiResponse.success(balance.get(), "월별 실제 금액을 성공적으로 조회했습니다.");
    }
    
    /**
     * 연도별 월별 실제 금액 목록 조회
     */
    public ApiResponse<List<MonthlyActualBalance>> getMonthlyActualBalancesByYear(Long userId, Integer year) {
        log.info("사용자 {}의 {}년 월별 실제 금액 목록 조회", userId, year);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        List<MonthlyActualBalance> balances = monthlyActualBalanceRepository.findByUserAndYear(user, year);
        
        return ApiResponse.success(balances, "월별 실제 금액 목록을 성공적으로 조회했습니다.");
    }
    
    /**
     * 월별 실제 금액 저장 또는 수정
     */
    @Transactional(readOnly = false)
    public ApiResponse<MonthlyActualBalance> saveMonthlyActualBalance(
            Long userId, 
            Integer year, 
            Integer month, 
            BigDecimal actualBalance,
            BigDecimal calculatedBalance) {
        log.info("사용자 {}의 {}년 {}월 실제 금액 저장: actualBalance={}, calculatedBalance={}", 
            userId, year, month, actualBalance, calculatedBalance);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<MonthlyActualBalance> existing = monthlyActualBalanceRepository.findByUserAndYearAndMonth(user, year, month);
        
        MonthlyActualBalance balance;
        if (existing.isPresent()) {
            balance = existing.get();
            balance.setActualBalance(actualBalance);
            if (calculatedBalance != null) {
                BigDecimal difference = actualBalance.subtract(calculatedBalance);
                balance.setDifference(difference);
            }
            log.info("월별 실제 금액 업데이트: balanceId={}", balance.getId());
        } else {
            BigDecimal difference = calculatedBalance != null 
                ? actualBalance.subtract(calculatedBalance) 
                : null;
            
            balance = MonthlyActualBalance.builder()
                .user(user)
                .year(year)
                .month(month)
                .actualBalance(actualBalance)
                .difference(difference)
                .build();
            log.info("월별 실제 금액 생성");
        }
        
        MonthlyActualBalance savedBalance = monthlyActualBalanceRepository.save(balance);
        return ApiResponse.success(savedBalance, "월별 실제 금액을 성공적으로 저장했습니다.");
    }
    
    /**
     * 월별 투자시드 증액 저장 또는 수정 (해당 월 순수 현금에서 차감, 홈 총 시드에 가산)
     */
    @Transactional(readOnly = false)
    public ApiResponse<MonthlyActualBalance> saveMonthlyInvestmentSeedAddition(
            Long userId, Integer year, Integer month, BigDecimal amount) {
        log.info("사용자 {}의 {}년 {}월 투자시드 증액 저장: amount={}", userId, year, month, amount);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<MonthlyActualBalance> existing = monthlyActualBalanceRepository.findByUserAndYearAndMonth(user, year, month);
        
        MonthlyActualBalance balance;
        if (existing.isPresent()) {
            balance = existing.get();
            balance.setInvestmentSeedAddition(amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null);
            log.info("월별 투자시드 증액 업데이트: balanceId={}", balance.getId());
        } else {
            balance = MonthlyActualBalance.builder()
                .user(user)
                .year(year)
                .month(month)
                .investmentSeedAddition(amount != null && amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null)
                .build();
            log.info("월별 투자시드 증액 생성");
        }
        
        MonthlyActualBalance savedBalance = monthlyActualBalanceRepository.save(balance);
        recomputeAndUpdatePortfolioTotalSeed(userId);
        return ApiResponse.success(savedBalance, "투자시드 증액을 성공적으로 저장했습니다.");
    }
    
    /**
     * 월별 실제 금액 삭제
     */
    @Transactional(readOnly = false)
    public ApiResponse<Void> deleteMonthlyActualBalance(Long userId, Integer year, Integer month) {
        log.info("사용자 {}의 {}년 {}월 실제 금액 삭제", userId, year, month);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        Optional<MonthlyActualBalance> existing = monthlyActualBalanceRepository.findByUserAndYearAndMonth(user, year, month);
        
        if (existing.isEmpty()) {
            return ApiResponse.success(null, "삭제할 실제 금액 기록이 없습니다.");
        }
        
        monthlyActualBalanceRepository.delete(existing.get());
        log.info("월별 실제 금액 삭제 완료: balanceId={}", existing.get().getId());
        
        recomputeAndUpdatePortfolioTotalSeed(userId);
        
        return ApiResponse.success(null, "월별 실제 금액을 성공적으로 삭제했습니다.");
    }
    
}

