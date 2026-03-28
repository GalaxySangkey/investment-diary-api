package com.investmentdiary.service;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.portfolio.PortfolioSummaryResponse;
import com.investmentdiary.entity.AssetType;
import com.investmentdiary.entity.InvestmentRecord;
import com.investmentdiary.entity.PortfolioSettings;
import com.investmentdiary.entity.User;
import com.investmentdiary.exception.PortfolioNotFoundException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.repository.InvestmentRecordRepository;
import com.investmentdiary.repository.PortfolioSettingsRepository;
import com.investmentdiary.repository.StockPriceRepository;
import com.investmentdiary.repository.StockDividendRepository;
import com.investmentdiary.repository.StockTickerMappingRepository;
import com.investmentdiary.repository.UserRepository;
import com.investmentdiary.repository.ExchangeRateRepository;
import com.investmentdiary.entity.StockPrice;
import com.investmentdiary.entity.StockDividend;
import com.investmentdiary.entity.ExchangeRate;
import com.investmentdiary.util.TickerConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional(readOnly = true)
public class PortfolioService {
    
    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);
    
    private final PortfolioSettingsRepository portfolioSettingsRepository;
    private final InvestmentRecordRepository investmentRecordRepository;
    private final UserRepository userRepository;
    private final PortfolioSettingsService portfolioSettingsService;
    private final StockPriceRepository stockPriceRepository;
    private final StockDividendRepository stockDividendRepository;
    private final StockTickerMappingRepository tickerMappingRepository;
    private final TickerConverter tickerConverter;
    private final ExchangeRateRepository exchangeRateRepository;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public PortfolioService(PortfolioSettingsRepository portfolioSettingsRepository,
                          InvestmentRecordRepository investmentRecordRepository,
                          UserRepository userRepository,
                          PortfolioSettingsService portfolioSettingsService,
                          StockPriceRepository stockPriceRepository,
                          StockDividendRepository stockDividendRepository,
                          StockTickerMappingRepository tickerMappingRepository,
                          TickerConverter tickerConverter,
                          ExchangeRateRepository exchangeRateRepository) {
        this.portfolioSettingsRepository = portfolioSettingsRepository;
        this.investmentRecordRepository = investmentRecordRepository;
        this.userRepository = userRepository;
        this.portfolioSettingsService = portfolioSettingsService;
        this.stockPriceRepository = stockPriceRepository;
        this.stockDividendRepository = stockDividendRepository;
        this.tickerMappingRepository = tickerMappingRepository;
        this.tickerConverter = tickerConverter;
        this.exchangeRateRepository = exchangeRateRepository;
    }
    
    /**
     * 포트폴리오 요약 조회
     */
    public ApiResponse<PortfolioSummaryResponse> getPortfolioSummary(Long userId) {
        log.info("사용자 {}의 포트폴리오 요약 조회", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 포트폴리오 설정이 없으면 기본 설정 생성
        PortfolioSettings settings = portfolioSettingsRepository.findByUserId(userId)
            .orElse(null);
        
        if (settings == null) {
            settings = portfolioSettingsService.createDefaultPortfolioSettings(user);
        }
        
        // 포트폴리오 요약 계산
        PortfolioSummaryResponse summary = calculatePortfolioSummary(userId, settings);
        
        return ApiResponse.success(summary, "포트폴리오 요약을 성공적으로 조회했습니다.");
    }
    
    /**
     * 포트폴리오 설정 조회
     */
    public ApiResponse<PortfolioSettings> getPortfolioSettings(Long userId) {
        log.info("사용자 {}의 포트폴리오 설정 조회", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 포트폴리오 설정이 없으면 기본 설정 생성
        PortfolioSettings settings = portfolioSettingsRepository.findByUserId(userId)
            .orElse(null);
        
        if (settings == null) {
            settings = portfolioSettingsService.createDefaultPortfolioSettings(user);
        }
        
        return ApiResponse.success(settings, "포트폴리오 설정을 성공적으로 조회했습니다.");
    }
    
    /**
     * 포트폴리오 설정 수정
     */
    @Transactional(readOnly = false)
    public ApiResponse<PortfolioSettings> updatePortfolioSettings(Long userId, PortfolioSettings updates) {
        log.info("사용자 {}의 포트폴리오 설정 수정", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 포트폴리오 설정이 없으면 기본 설정 생성
        PortfolioSettings settings = portfolioSettingsRepository.findByUserId(userId)
            .orElse(null);
        
        if (settings == null) {
            settings = portfolioSettingsService.createDefaultPortfolioSettings(user);
        }
        
        // updates가 null이면 기존 설정 반환
        if (updates == null) {
            return ApiResponse.success(settings, "포트폴리오 설정을 조회했습니다.");
        }
        
        // 수정 가능한 필드들 업데이트
        if (updates.getTotalSeed() != null) {
            settings.setTotalSeed(updates.getTotalSeed());
        }
        if (updates.getCurrency() != null) {
            settings.setCurrency(updates.getCurrency());
        }
        if (updates.getRiskTolerance() != null) {
            settings.setRiskTolerance(updates.getRiskTolerance());
        }
        if (updates.getInvestmentGoal() != null) {
            settings.setInvestmentGoal(updates.getInvestmentGoal());
        }
        if (updates.getTargetProfitRate() != null) {
            settings.setTargetProfitRate(updates.getTargetProfitRate());
        }
        if (updates.getMaxSingleStockRatio() != null) {
            settings.setMaxSingleStockRatio(updates.getMaxSingleStockRatio());
        }
        if (updates.getMaxSectorRatio() != null) {
            settings.setMaxSectorRatio(updates.getMaxSectorRatio());
        }
        if (updates.getRebalancingFrequency() != null) {
            settings.setRebalancingFrequency(updates.getRebalancingFrequency());
        }
        if (updates.getAutoRebalancingEnabled() != null) {
            settings.setAutoRebalancingEnabled(updates.getAutoRebalancingEnabled());
        }
        if (updates.getDividendReinvestmentEnabled() != null) {
            settings.setDividendReinvestmentEnabled(updates.getDividendReinvestmentEnabled());
        }
        if (updates.getTaxOptimizationEnabled() != null) {
            settings.setTaxOptimizationEnabled(updates.getTaxOptimizationEnabled());
        }
        if (updates.getNotificationEnabled() != null) {
            settings.setNotificationEnabled(updates.getNotificationEnabled());
        }
        if (updates.getProfitAlertThreshold() != null) {
            settings.setProfitAlertThreshold(updates.getProfitAlertThreshold());
        }
        if (updates.getLossAlertThreshold() != null) {
            settings.setLossAlertThreshold(updates.getLossAlertThreshold());
        }
        
        PortfolioSettings updatedSettings = portfolioSettingsRepository.save(settings);
        
        log.info("포트폴리오 설정 수정 완료: {}", updatedSettings.getId());
        
        return ApiResponse.success(updatedSettings, "포트폴리오 설정이 성공적으로 수정되었습니다.");
    }
    
    /**
     * 포트폴리오 요약 계산
     */
    private PortfolioSummaryResponse calculatePortfolioSummary(Long userId, PortfolioSettings settings) {
        // 총 투자 금액 계산
        BigDecimal totalInvestment = investmentRecordRepository.getTotalInvestmentAmount(userId)
            .orElse(BigDecimal.ZERO);
        
        // 총 실현 손익 계산
        BigDecimal totalRealizedProfit = investmentRecordRepository.getTotalRealizedProfit(userId)
            .orElse(BigDecimal.ZERO);
        
        // 미실현 손익 계산
        BigDecimal totalUnrealizedProfit = calculateUnrealizedProfit(userId);
        
        // 총 손익 계산
        BigDecimal totalProfit = totalRealizedProfit.add(totalUnrealizedProfit);
        
        // 총 수익률 계산
        BigDecimal totalProfitRate = BigDecimal.ZERO;
        if (settings.getTotalSeed().compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate = totalProfit
                .divide(settings.getTotalSeed(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        
        // 배당 수익 계산
        DividendCalculationResult dividendResult = calculateTotalDividend(userId);
        BigDecimal totalDividendAmount = dividendResult.getTotalDividend();
        Integer dividendYear = dividendResult.getDividendYear();
        BigDecimal totalDividendRate = BigDecimal.ZERO;
        if (settings.getTotalSeed().compareTo(BigDecimal.ZERO) > 0) {
            totalDividendRate = totalDividendAmount
                .divide(settings.getTotalSeed(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        
        return PortfolioSummaryResponse.builder()
            .totalSeed(settings.getTotalSeed())
            .totalInvestment(totalInvestment)
            .totalProfitRate(totalProfitRate)
            .totalDividendRate(totalDividendRate)
            .totalProfitAmount(totalProfit)
            .totalDividendAmount(totalDividendAmount)
            .dividendYear(dividendYear)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * 미실현 손익 계산
     * DB에 저장된 최신 종가를 사용
     * 사용자의 기본 통화로 환율 적용하여 계산
     */
    private BigDecimal calculateUnrealizedProfit(Long userId) {
        // 사용자의 기본 통화 가져오기
        String userCurrency = portfolioSettingsRepository.findByUserId(userId)
            .map(settings -> settings.getCurrency())
            .orElse("KRW"); // 기본값: KRW
        
        List<InvestmentRecord> buyRecords = investmentRecordRepository
            .findByUserIdAndType(userId, InvestmentRecord.InvestmentType.BUY);
        
        BigDecimal totalUnrealizedProfit = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        
        for (InvestmentRecord record : buyRecords) {
            // 외환 기록은 미실현 손익 계산에서 제외 (외환은 환율 변동으로 인한 손익이므로 별도 처리 필요)
            if (record.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (record.getPricePerShare() == null || record.getQuantity() == null) {
                continue;
            }
            
            // DB에서 최신 종가 조회
            BigDecimal currentPrice = null;
            String stockCurrency = null;
            if (record.getStockCode() != null && !record.getStockCode().isEmpty()) {
                Optional<StockPrice> stockPrice = stockPriceRepository
                    .findLatestByStockCode(record.getStockCode());
                if (stockPrice.isPresent()) {
                    currentPrice = stockPrice.get().getClosePrice();
                    stockCurrency = stockPrice.get().getCurrency();
                }
            }
            
            // DB에 종가가 없으면 record의 currentPrice 사용 (하위 호환성)
            if (currentPrice == null) {
                currentPrice = record.getCurrentPrice();
                // 통화 정보가 없으면 기본값 (한국 주식으로 가정)
                if (stockCurrency == null) {
                    stockCurrency = "KRW";
                }
            }
            
            if (currentPrice != null) {
                // 환율 적용하여 사용자 통화로 변환
                BigDecimal exchangeRate = getExchangeRate(stockCurrency, userCurrency, today);
                BigDecimal currentPriceInUserCurrency = currentPrice.multiply(exchangeRate);
                BigDecimal originalPriceInUserCurrency = record.getPricePerShare().multiply(exchangeRate);
                
                // quantity는 이미 BigDecimal이므로 valueOf 불필요
                BigDecimal currentValue = currentPriceInUserCurrency
                    .multiply(record.getQuantity());
                BigDecimal originalValue = originalPriceInUserCurrency
                    .multiply(record.getQuantity());
                
                BigDecimal unrealizedProfit = currentValue.subtract(originalValue);
                totalUnrealizedProfit = totalUnrealizedProfit.add(unrealizedProfit);
            }
        }
        
        return totalUnrealizedProfit;
    }
    
    /**
     * 배당 계산 결과를 담는 내부 클래스
     */
    private static class DividendCalculationResult {
        private final BigDecimal totalDividend;
        private final Integer dividendYear;
        
        public DividendCalculationResult(BigDecimal totalDividend, Integer dividendYear) {
            this.totalDividend = totalDividend;
            this.dividendYear = dividendYear;
        }
        
        public BigDecimal getTotalDividend() {
            return totalDividend;
        }
        
        public Integer getDividendYear() {
            return dividendYear;
        }
    }
    
    /**
     * 총 배당 수익 계산 (보유 수량 기준, 전년도 배당 총액 사용)
     * DB에 저장된 배당 정보를 사용
     * 사용자의 기본 통화로 환율 적용하여 계산
     * 
     * 배당 기준 연도 결정 로직:
     * - 현재 날짜가 7월 이후인 경우: 작년 7월 이전 배당은 재작년 배당으로 간주
     * - 현재 날짜가 7월 이전인 경우: 작년 배당 사용
     */
    private DividendCalculationResult calculateTotalDividend(Long userId) {
        // 사용자의 기본 통화 가져오기
        String userCurrency = portfolioSettingsRepository.findByUserId(userId)
            .map(settings -> settings.getCurrency())
            .orElse("KRW"); // 기본값: KRW
        
        List<InvestmentRecord> allRecords = investmentRecordRepository.findActiveRecordsByUserId(userId);
        
        // 종목별로 그룹화 (stockName + stockCode 기준)
        Map<String, List<InvestmentRecord>> stockGroups = allRecords.stream()
            .filter(r -> r.getStockName() != null && !r.getStockName().isEmpty())
            .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY || r.getType() == InvestmentRecord.InvestmentType.SELL)
            .collect(java.util.stream.Collectors.groupingBy(r -> {
                String stockCode = r.getStockCode() != null ? r.getStockCode() : "";
                return r.getStockName() + "|" + stockCode;
            }));
        
        BigDecimal totalDividend = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();
        
        // 배당 기준 연도 결정: 7월 이후면 작년 7월 이전 배당은 재작년 배당으로 간주
        // 배당 정보는 보통 연초에 정리해서 주기 때문에, 7월 이후에 조회할 때는
        // 작년 7월 이전 배당은 재작년 배당으로 간주하고, 작년 7월 이후 배당만 작년 배당으로 간주
        int dividendYear;
        LocalDate dividendYearStart;
        LocalDate dividendYearEnd;
        
        if (currentMonth >= 7) {
            // 7월 이후: 작년 7월 이후 배당만 작년 배당으로 간주
            // 작년 7월 이전 배당은 재작년 배당으로 간주하므로, 재작년 전체 배당 사용
            dividendYear = currentYear - 2; // 재작년
            dividendYearStart = LocalDate.of(dividendYear, 1, 1); // 재작년 1월 1일부터
            dividendYearEnd = LocalDate.of(dividendYear, 12, 31); // 재작년 12월 31일까지
        } else {
            // 7월 이전: 재작년 배당 사용
            dividendYear = currentYear - 2; // 재작년
            dividendYearStart = LocalDate.of(dividendYear, 1, 1); // 재작년 1월 1일부터
            dividendYearEnd = LocalDate.of(dividendYear, 12, 31); // 재작년 12월 31일까지
        }
        
        log.info("배당 기준 연도 결정: currentDate={}, currentMonth={}, dividendYear={}, dividendYearRange={} ~ {}", 
            today, currentMonth, dividendYear, dividendYearStart, dividendYearEnd);
        
        for (Map.Entry<String, List<InvestmentRecord>> entry : stockGroups.entrySet()) {
            List<InvestmentRecord> stockRecords = entry.getValue();
            if (stockRecords.isEmpty()) continue;
            
            // 종목 정보 (첫 번째 기록에서 가져옴)
            InvestmentRecord firstRecord = stockRecords.get(0);
            String stockName = firstRecord.getStockName();
            String stockCode = firstRecord.getStockCode();
            
            // 현재 보유 수량 계산 (BigDecimal로 처리)
            // 현재 보유 수량 계산 (BigDecimal로 처리)
            BigDecimal totalBuyQuantity = stockRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY)
                .filter(r -> r.getQuantity() != null)
                .map(InvestmentRecord::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalSellQuantity = stockRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.SELL)
                .filter(r -> r.getSellQuantity() != null)
                .map(InvestmentRecord::getSellQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal holdingQuantity = totalBuyQuantity.subtract(totalSellQuantity);
            if (holdingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 보유 수량이 없으면 배당 계산 안 함
            }
            
            // 전년도 배당 총액 조회
            BigDecimal lastYearDividendPerShare = null;
            String dividendCurrency = null;
            
            if (stockCode != null && !stockCode.isEmpty()) {
                // 티커 찾기
                String ticker = tickerMappingRepository.findByStockCode(stockCode)
                    .filter(mapping -> mapping.getIsActive())
                    .map(mapping -> mapping.getTicker())
                    .orElse(tickerConverter.convertToYahooTicker(stockCode, "KOSPI"));
                
                if (ticker != null) {
                    // 배당 기준 연도의 모든 배당 기록 조회 (배당일이 있는 경우)
                    List<StockDividend> dividendYearDividends = stockDividendRepository
                        .findByTickerAndDividendDateBetween(ticker, dividendYearStart, dividendYearEnd);
                    
                    // 배당일이 null인 배당 정보도 조회 (연간 배당금 정보만 있는 경우)
                    List<StockDividend> dividendsWithoutDate = stockDividendRepository
                        .findByTickerOrderByDividendDateDesc(ticker).stream()
                        .filter(d -> d.getDividendDate() == null)
                        .collect(java.util.stream.Collectors.toList());
                    
                    // 배당 기준 연도 배당 총액 합산 (분기별, 월별 배당 모두 합산)
                    BigDecimal totalDividendYearDividend = BigDecimal.ZERO;
                    if (!dividendYearDividends.isEmpty()) {
                        for (StockDividend dividend : dividendYearDividends) {
                            if (dividend.getDividendPerShare() != null) {
                                totalDividendYearDividend = totalDividendYearDividend.add(dividend.getDividendPerShare());
                            }
                            if (dividend.getCurrency() != null) {
                                dividendCurrency = dividend.getCurrency();
                            }
                        }
                        lastYearDividendPerShare = totalDividendYearDividend;
                        log.info("배당 기준 연도 배당 총액 계산 (배당일 기준): stockName={}, stockCode={}, ticker={}, dividendYear={}, dividend={}, dividendCount={}, holdingQty={}", 
                            stockName, stockCode, ticker, dividendYear, lastYearDividendPerShare, dividendYearDividends.size(), holdingQuantity);
                    } else if (!dividendsWithoutDate.isEmpty()) {
                        // 배당일이 null인 경우는 연간 배당금 정보만 있는 경우
                        // 가장 최근 배당 정보 사용
                        StockDividend dividend = dividendsWithoutDate.get(0);
                        lastYearDividendPerShare = dividend.getAnnualDividend() != null 
                            ? dividend.getAnnualDividend() 
                            : dividend.getDividendPerShare();
                        dividendCurrency = dividend.getCurrency();
                        log.info("배당 기준 연도 배당 총액 계산 (배당일 없음, 연간 배당금 사용): stockName={}, stockCode={}, ticker={}, dividendYear={}, dividend={}, holdingQty={}", 
                            stockName, stockCode, ticker, dividendYear, lastYearDividendPerShare, holdingQuantity);
                    } else {
                        // 배당 기준 연도 배당이 없으면 최근 배당 정보 조회 (fallback)
                        List<StockDividend> allDividends = stockDividendRepository.findByTickerOrderByDividendDateDesc(ticker);
                        if (!allDividends.isEmpty()) {
                            Optional<StockDividend> latestDividend = allDividends.stream()
                                .filter(dividend -> {
                                    LocalDate dividendDate = dividend.getDividendDate();
                                    return dividendDate != null && !dividendDate.isAfter(today);
                                })
                                .findFirst();
                            
                            if (latestDividend.isPresent()) {
                                StockDividend dividend = latestDividend.get();
                                lastYearDividendPerShare = dividend.getAnnualDividend() != null 
                                    ? dividend.getAnnualDividend() 
                                    : dividend.getDividendPerShare();
                                dividendCurrency = dividend.getCurrency();
                                log.info("배당 기준 연도 배당이 없어 최근 배당 사용: stockName={}, stockCode={}, dividendYear={}, dividendPerShare={}, dividendDate={}", 
                                    stockName, stockCode, dividendYear, lastYearDividendPerShare, dividend.getDividendDate());
                            }
                        }
                    }
                }
            }
            
            // DB에 배당 정보가 없으면 record의 dividendPerShare 사용 (하위 호환성)
            if (lastYearDividendPerShare == null) {
                // 모든 매수 기록에서 dividendPerShare 찾기
                Optional<InvestmentRecord> recordWithDividend = stockRecords.stream()
                    .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY)
                    .filter(r -> r.getDividendPerShare() != null)
                    .findFirst();
                
                if (recordWithDividend.isPresent()) {
                    lastYearDividendPerShare = recordWithDividend.get().getDividendPerShare();
                    dividendCurrency = "KRW"; // 기본값
                }
            }
            
            if (lastYearDividendPerShare != null && lastYearDividendPerShare.compareTo(BigDecimal.ZERO) > 0) {
                // 통화 정보가 없으면 기본값
                if (dividendCurrency == null) {
                    dividendCurrency = "KRW";
                }
                
                // 환율 적용하여 사용자 통화로 변환
                BigDecimal exchangeRate = getExchangeRate(dividendCurrency, userCurrency, today);
                BigDecimal dividendInUserCurrency = lastYearDividendPerShare.multiply(exchangeRate);
                
                // 보유 수량 × 전년도 배당 총액 = 해당 종목의 배당금액
                BigDecimal stockDividend = dividendInUserCurrency
                    .multiply(holdingQuantity);
                totalDividend = totalDividend.add(stockDividend);
                
                log.info("종목 배당금액 계산: stockName={}, stockCode={}, holdingQty={}, lastYearDividend={}, exchangeRate={}, dividendInUserCurrency={}, stockDividend={}", 
                    stockName, stockCode, holdingQuantity, lastYearDividendPerShare, exchangeRate, dividendInUserCurrency, stockDividend);
            } else {
                log.warn("배당 정보 없음: stockName={}, stockCode={}, holdingQty={}, lastYearDividend={}", 
                    stockName, stockCode, holdingQuantity, lastYearDividendPerShare);
            }
        }
        
        log.info("총 배당금액 계산 완료: userId={}, totalDividend={}, dividendYear={}, stockCount={}", 
            userId, totalDividend, dividendYear, stockGroups.size());
        return new DividendCalculationResult(totalDividend, dividendYear);
    }
    
    /**
     * 환율 조회 (기준 통화 -> 목표 통화)
     * @param fromCurrency 기준 통화
     * @param toCurrency 목표 통화
     * @param date 날짜 (null이면 최신 환율)
     * @return 환율 (1 fromCurrency = ? toCurrency)
     */
    private BigDecimal getExchangeRate(String fromCurrency, String toCurrency, LocalDate date) {
        // 같은 통화면 1.0 반환
        if (fromCurrency == null || toCurrency == null || fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        // 날짜가 null이면 오늘 날짜 사용
        if (date == null) {
            date = LocalDate.now();
        }
        
        try {
            // 직접 환율 조회 시도 (예: USD -> KRW)
            String currencyPair = fromCurrency + toCurrency;
            Optional<ExchangeRate> exchangeRate = exchangeRateRepository
                .findByCurrencyPairAndRateDate(currencyPair, date);
            
            if (exchangeRate.isPresent()) {
                return exchangeRate.get().getRate();
            }
            
            // 최신 환율 조회 시도
            exchangeRate = exchangeRateRepository.findLatestByCurrencyPair(currencyPair);
            if (exchangeRate.isPresent()) {
                return exchangeRate.get().getRate();
            }
            
            // 역환율 조회 시도 (예: KRW -> USD는 USD -> KRW의 역수)
            String reversePair = toCurrency + fromCurrency;
            Optional<ExchangeRate> reverseRate = exchangeRateRepository
                .findByCurrencyPairAndRateDate(reversePair, date);
            
            if (reverseRate.isPresent()) {
                // 역환율 계산: 1 / 역환율
                return BigDecimal.ONE.divide(reverseRate.get().getRate(), 8, RoundingMode.HALF_UP);
            }
            
            // 최신 역환율 조회 시도
            reverseRate = exchangeRateRepository.findLatestByCurrencyPair(reversePair);
            if (reverseRate.isPresent()) {
                return BigDecimal.ONE.divide(reverseRate.get().getRate(), 8, RoundingMode.HALF_UP);
            }
            
            // KRW를 기준으로 환율 계산 (예: USD -> EUR는 USD -> KRW / EUR -> KRW)
            if (!fromCurrency.equals("KRW") && !toCurrency.equals("KRW")) {
                Optional<ExchangeRate> fromToKrw = exchangeRateRepository
                    .findLatestByCurrencyPair(fromCurrency + "KRW");
                Optional<ExchangeRate> toToKrw = exchangeRateRepository
                    .findLatestByCurrencyPair(toCurrency + "KRW");
                
                if (fromToKrw.isPresent() && toToKrw.isPresent()) {
                    // USD -> KRW / EUR -> KRW = USD -> EUR
                    return fromToKrw.get().getRate()
                        .divide(toToKrw.get().getRate(), 8, RoundingMode.HALF_UP);
                }
            }
            
            log.warn("환율 정보를 찾을 수 없습니다: {} -> {} (날짜: {})", fromCurrency, toCurrency, date);
            return BigDecimal.ONE; // 환율 정보가 없으면 1.0 반환 (변환 없음)
            
        } catch (Exception e) {
            log.error("환율 조회 중 오류 발생: {} -> {} (날짜: {})", fromCurrency, toCurrency, date, e);
            return BigDecimal.ONE; // 오류 발생 시 1.0 반환
        }
    }
    
    /**
     * 포트폴리오 분산 분석
     */
    public ApiResponse<Map<String, Object>> getPortfolioDiversification(Long userId) {
        log.info("사용자 {}의 포트폴리오 분산 분석", userId);
        
        List<Object[]> sectorDistribution = investmentRecordRepository.getSectorDistribution(userId);
        
        // 분산 점수 계산
        double diversificationScore = calculateDiversificationScore(sectorDistribution);
        
        // 리스크 점수 계산
        double riskScore = calculateRiskScore(userId);
        
        Map<String, Object> analysis = Map.of(
            "totalStocks", sectorDistribution.size(),
            "sectorDistribution", sectorDistribution,
            "riskScore", riskScore,
            "diversificationScore", diversificationScore
        );
        
        return ApiResponse.success(analysis, "포트폴리오 분산 분석이 완료되었습니다.");
    }
    
    /**
     * 분산 점수 계산
     */
    private double calculateDiversificationScore(List<Object[]> sectorDistribution) {
        if (sectorDistribution.isEmpty()) {
            return 0.0;
        }
        
        // Herfindahl-Hirschman Index (HHI) 계산
        double hhi = sectorDistribution.stream()
            .mapToDouble(row -> {
                BigDecimal ratio = (BigDecimal) row[1]; // ratio column
                return Math.pow(ratio.doubleValue() / 100.0, 2);
            })
            .sum();
        
        // HHI를 0-10 점수로 변환 (낮을수록 좋음)
        double score = Math.max(0, 10 - (hhi * 10));
        
        return Math.round(score * 10.0) / 10.0; // 소수점 첫째 자리까지 반올림
    }
    
    /**
     * 리스크 점수 계산
     */
    private double calculateRiskScore(Long userId) {
        // 간단한 리스크 점수 계산 (실제로는 더 복잡한 공식 사용)
        List<InvestmentRecord> records = investmentRecordRepository
            .findActiveRecordsByUserId(userId);
        
        if (records.isEmpty()) {
            return 5.0; // 중간 리스크
        }
        
        // 변동성 기반 리스크 점수 계산
        double totalRisk = 0.0;
        int count = 0;
        
        for (InvestmentRecord record : records) {
            if (record.getUnrealizedProfitRate() != null) {
                double profitRate = record.getUnrealizedProfitRate().doubleValue();
                // 수익률의 절댓값이 클수록 리스크가 높음
                totalRisk += Math.abs(profitRate);
                count++;
            }
        }
        
        if (count == 0) {
            return 5.0;
        }
        
        double avgRisk = totalRisk / count;
        // 0-10 점수로 정규화
        double score = Math.min(10.0, avgRisk / 10.0);
        
        return Math.round(score * 10.0) / 10.0;
    }
    
    /**
     * 자동 리밸런싱 필요 여부 확인
     */
    public ApiResponse<Map<String, Object>> checkRebalancingNeeded(Long userId) {
        log.info("사용자 {}의 리밸런싱 필요 여부 확인", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 포트폴리오 설정이 없으면 기본 설정 생성
        PortfolioSettings settings = portfolioSettingsRepository.findByUserId(userId)
            .orElse(null);
        
        if (settings == null) {
            settings = portfolioSettingsService.createDefaultPortfolioSettings(user);
        }
        
        boolean needsRebalancing = settings.needsRebalancing();
        
        Map<String, Object> result = Map.of(
            "needsRebalancing", needsRebalancing,
            "lastRebalancingDate", settings.getLastRebalancingDate(),
            "rebalancingFrequency", settings.getRebalancingFrequency(),
            "autoRebalancingEnabled", settings.getAutoRebalancingEnabled()
        );
        
        return ApiResponse.success(result, "리밸런싱 필요 여부를 확인했습니다.");
    }
    
    /**
     * 포트폴리오 성과 분석
     */
    public ApiResponse<Map<String, Object>> getPortfolioPerformance(Long userId, String period) {
        log.info("사용자 {}의 포트폴리오 성과 분석: {}", userId, period);
        
        LocalDate startDate = calculateStartDate(period);
        LocalDate endDate = LocalDate.now();
        
        // 기간별 투자 기록 조회
        List<InvestmentRecord> records = investmentRecordRepository
            .findRecordsByDateRange(userId, startDate, endDate);
        
        // 성과 지표 계산
        Map<String, Object> performance = calculatePerformanceMetrics(records, period);
        
        return ApiResponse.success(performance, "포트폴리오 성과 분석이 완료되었습니다.");
    }
    
    /**
     * 분석 기간 시작 날짜 계산
     */
    private LocalDate calculateStartDate(String period) {
        LocalDate now = LocalDate.now();
        
        return switch (period) {
            case "1M" -> now.minusMonths(1);
            case "3M" -> now.minusMonths(3);
            case "6M" -> now.minusMonths(6);
            case "1Y" -> now.minusYears(1);
            default -> now.minusMonths(1);
        };
    }
    
    /**
     * 성과 지표 계산
     */
    private Map<String, Object> calculatePerformanceMetrics(List<InvestmentRecord> records, String period) {
        // 실제 구현에서는 더 복잡한 성과 지표 계산
        BigDecimal totalInvestment = BigDecimal.ZERO;
        BigDecimal totalReturn = BigDecimal.ZERO;
        
        for (InvestmentRecord record : records) {
            if (record.getTotalAmount() != null) {
                totalInvestment = totalInvestment.add(record.getTotalAmount());
            }
            if (record.getUnrealizedProfitAmount() != null) {
                totalReturn = totalReturn.add(record.getUnrealizedProfitAmount());
            }
        }
        
        BigDecimal returnRate = BigDecimal.ZERO;
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            returnRate = totalReturn
                .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        
        return Map.of(
            "period", period,
            "totalInvestment", totalInvestment,
            "totalReturn", totalReturn,
            "returnRate", returnRate,
            "recordCount", records.size()
        );
    }
    
} 