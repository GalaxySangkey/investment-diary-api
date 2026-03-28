package com.investmentdiary.service;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.entity.AssetType;
import com.investmentdiary.entity.InvestmentRecord;
import com.investmentdiary.entity.StockPrice;
import com.investmentdiary.entity.PortfolioSettings;
import com.investmentdiary.repository.InvestmentRecordRepository;
import com.investmentdiary.repository.StockPriceRepository;
import com.investmentdiary.repository.PortfolioSettingsRepository;
import com.investmentdiary.repository.ExchangeRateRepository;
import com.investmentdiary.repository.StockTickerMappingRepository;
import com.investmentdiary.entity.ExchangeRate;
import com.investmentdiary.entity.StockTickerMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional(readOnly = true)
public class CalendarService {
    
    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);
    
    private final InvestmentRecordRepository investmentRecordRepository;
    private final StockPriceRepository stockPriceRepository;
    private final PortfolioSettingsRepository portfolioSettingsRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final StockTickerMappingRepository tickerMappingRepository;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public CalendarService(InvestmentRecordRepository investmentRecordRepository,
                         StockPriceRepository stockPriceRepository,
                         PortfolioSettingsRepository portfolioSettingsRepository,
                         ExchangeRateRepository exchangeRateRepository,
                         StockTickerMappingRepository tickerMappingRepository) {
        this.investmentRecordRepository = investmentRecordRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.portfolioSettingsRepository = portfolioSettingsRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.tickerMappingRepository = tickerMappingRepository;
    }
    
    /**
     * 월별 캘린더 데이터 조회 (성능 최적화 버전)
     * 
     * 최적화 내용:
     * - 투자 기록: 1회 조회 (기존: 매일 2회 × 31일 = 62회)
     * - 종가 데이터: 1회 배치 조회 (기존: 매일 종목수×2 = 620+회)
     * - 환율: 1회 조회 (기존: 매일 종목수 = 310+회)
     * - 한국주식 여부: 1회 조회 (기존: 매일 종목수 = 310+회)
     * 총: ~1000+회 → ~10회 이하로 감소
     */
    public ApiResponse<Map<String, Object>> getCalendarData(Long userId, int year, int month) {
        log.info("사용자 {}의 {}-{} 캘린더 데이터 조회 시작", userId, year, month);
        long startTime = System.currentTimeMillis();
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        // 해당 월의 투자 기록 조회 (일별 표시용)
        List<InvestmentRecord> records = investmentRecordRepository
            .findRecordsByDateRange(userId, startDate, endDate);
        
        // 날짜별로 그룹화
        Map<LocalDate, List<InvestmentRecord>> recordsByDate = records.stream()
            .collect(Collectors.groupingBy(InvestmentRecord::getRecordDate));
        
        // ===== 성능 최적화: 데이터 사전 로딩 =====
        
        // 1. 모든 투자 기록 한 번에 조회 (매일 1900~date 재조회 방지)
        List<InvestmentRecord> allUserRecords = investmentRecordRepository
            .findRecordsByDateRange(userId, LocalDate.of(1900, 1, 1), endDate);
        
        // 2. 보유 종목 코드 추출
        Set<String> allStockCodes = allUserRecords.stream()
            .filter(r -> r.getStockCode() != null && !r.getStockCode().isEmpty()
                      && r.getAssetType() != AssetType.CURRENCY)
            .map(InvestmentRecord::getStockCode)
            .collect(Collectors.toSet());
        
        // 3. 종목 가격 데이터 일괄 조회 (15일 여유 - 주말/공휴일/전 거래일 대비)
        LocalDate priceStartDate = startDate.minusDays(15);
        Map<String, Map<LocalDate, StockPrice>> priceCache = new HashMap<>();
        if (!allStockCodes.isEmpty()) {
            List<StockPrice> allPrices = stockPriceRepository
                .findByStockCodesAndDateRange(new ArrayList<>(allStockCodes), priceStartDate, endDate);
            for (StockPrice sp : allPrices) {
                priceCache.computeIfAbsent(sp.getStockCode(), k -> new HashMap<>())
                    .put(sp.getPriceDate(), sp);
            }
        }
        
        // 4. 사용자 통화 설정
        String userCurrency = portfolioSettingsRepository.findByUserId(userId)
            .map(PortfolioSettings::getCurrency)
            .orElse("KRW");
        
        // 5. 환율 캐시 (주요 통화쌍 최신 환율 한 번만 조회)
        Map<String, BigDecimal> exchangeRateCache = new HashMap<>();
        for (String pair : Arrays.asList("USDKRW", "JPYKRW", "EURKRW", "CNYKRW", "GBPKRW")) {
            exchangeRateRepository.findLatestByCurrencyPair(pair)
                .ifPresent(er -> {
                    exchangeRateCache.put(pair, er.getRate());
                    String reversePair = pair.substring(3) + pair.substring(0, 3);
                    exchangeRateCache.put(reversePair, 
                        BigDecimal.ONE.divide(er.getRate(), 8, RoundingMode.HALF_UP));
                });
        }
        
        // 6. 한국 주식 여부 캐시
        Map<String, Boolean> koreanStockCache = new HashMap<>();
        for (String stockCode : allStockCodes) {
            koreanStockCache.put(stockCode, isKoreanStock(stockCode));
        }
        
        CalendarCacheData cache = new CalendarCacheData(
            allUserRecords, priceCache, exchangeRateCache, userCurrency, koreanStockCache);
        
        // 캘린더 데이터 생성 (캐시 사용)
        List<Map<String, Object>> calendarDays = createCalendarDaysCached(yearMonth, recordsByDate, userId, cache);
        
        Map<String, Object> calendarData = Map.of(
            "year", year,
            "month", month,
            "days", calendarDays
        );
        
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("사용자 {}의 {}-{} 캘린더 데이터 조회 완료 ({}ms, 종목수: {})", 
            userId, year, month, elapsed, allStockCodes.size());
        
        return ApiResponse.success(calendarData, "캘린더 데이터를 성공적으로 조회했습니다.");
    }
    
    /**
     * 특정 날짜 투자 기록 조회
     */
    public ApiResponse<List<InvestmentRecord>> getRecordsByDate(Long userId, LocalDate date) {
        log.info("사용자 {}의 {} 투자 기록 조회", userId, date);
        
        List<InvestmentRecord> records = investmentRecordRepository
            .findByUserIdAndRecordDate(userId, date);
        
        return ApiResponse.success(records, "해당 날짜의 투자 기록을 조회했습니다.");
    }
    
    /**
     * 특정 날짜의 보유 종목 목록과 상세 정보 조회
     */
    public ApiResponse<Map<String, Object>> getDailyPortfolio(Long userId, LocalDate date) {
        log.info("사용자 {}의 {} 보유 종목 조회", userId, date);
        
        // 해당 날짜까지의 보유 종목 계산
        List<InvestmentRecord> allBuyRecords = investmentRecordRepository
            .findRecordsByDateRange(userId, LocalDate.of(1900, 1, 1), date);
        
        // 보유 종목 계산 (매수 수량 - 매도 수량)
        Map<String, HoldingInfo> holdings = new HashMap<>();
        
        for (InvestmentRecord record : allBuyRecords) {
            // 외환 기록은 일일 포트폴리오에서 제외 (외환은 환율 변동으로 인한 손익이므로 별도 처리 필요)
            if (record.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (record.getType() != InvestmentRecord.InvestmentType.BUY || 
                record.getStockCode() == null || record.getQuantity() == null) {
                continue;
            }
            
            String stockCode = record.getStockCode();
            HoldingInfo holding = holdings.getOrDefault(stockCode, new HoldingInfo());
            holding.addBuyRecord(record);
            holdings.put(stockCode, holding);
        }
        
        // 매도 기록으로 보유 수량 조정
        for (InvestmentRecord sellRecord : allBuyRecords) {
            // 외환 기록은 일일 포트폴리오에서 제외
            if (sellRecord.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (sellRecord.getType() != InvestmentRecord.InvestmentType.SELL ||
                sellRecord.getStockCode() == null || sellRecord.getSellQuantity() == null) {
                continue;
            }
            
            String stockCode = sellRecord.getStockCode();
            if (holdings.containsKey(stockCode)) {
                holdings.get(stockCode).subtractSellQuantity(sellRecord.getSellQuantity());
            }
        }
        
        // 보유 수량이 0보다 큰 종목만 필터링
        holdings.entrySet().removeIf(entry -> entry.getValue().getHoldingQuantity().compareTo(BigDecimal.ZERO) <= 0);
        
        // 사용자의 기본 통화 가져오기
        String userCurrency = portfolioSettingsRepository.findByUserId(userId)
            .map(PortfolioSettings::getCurrency)
            .orElse("KRW");
        
        // 주말인 경우 수익률 변동치를 계산하지 않음
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY);
        
        // 각 보유 종목의 상세 정보 계산
        List<Map<String, Object>> stockDetails = new ArrayList<>();
        
        for (Map.Entry<String, HoldingInfo> entry : holdings.entrySet()) {
            String stockCode = entry.getKey();
            HoldingInfo holding = entry.getValue();
            
            // 첫 번째 매수 기록에서 종목명 가져오기
            Optional<InvestmentRecord> firstBuyRecordOpt = allBuyRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY)
                .filter(r -> stockCode.equals(r.getStockCode()))
                .findFirst();
            
            String stockName = firstBuyRecordOpt
                .map(InvestmentRecord::getStockName)
                .orElse("");
            
            // 종목별 이전 거래일 계산 (한국 주식은 한국 공휴일 고려, 미국 주식은 주말만 고려)
            LocalDate previousDate = getPreviousTradingDateForStock(stockCode, date);
            
            // 전날 종가 조회
            Optional<StockPrice> previousPrice = stockPriceRepository
                .findByStockCodeAndPriceDate(stockCode, previousDate);
            
            // 전날 종가가 없으면 더 이전 거래일을 찾아서 조회 (미국 주식의 경우 한국 공휴일에도 거래 가능)
            if (!previousPrice.isPresent()) {
                boolean isKorean = isKoreanStock(stockCode);
                LocalDate searchDate = previousDate.minusDays(1);
                int searchDays = 0;
                int maxSearchDays = 10; // 최대 10일 전까지 검색
                
                while (searchDays < maxSearchDays && !previousPrice.isPresent()) {
                    java.time.DayOfWeek searchDayOfWeek = searchDate.getDayOfWeek();
                    boolean isSearchWeekend = (searchDayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                              searchDayOfWeek == java.time.DayOfWeek.SUNDAY);
                    
                    // 주말은 건너뛰기
                    if (isSearchWeekend) {
                        searchDate = searchDate.minusDays(1);
                        searchDays++;
                        continue;
                    }
                    
                    // 한국 주식인 경우에만 공휴일 체크
                    if (isKorean) {
                        boolean isSearchHoliday = isKoreanHoliday(searchDate);
                        if (isSearchHoliday) {
                            searchDate = searchDate.minusDays(1);
                            searchDays++;
                            continue;
                        }
                    }
                    
                    // 주말도 아니고 (한국 주식인 경우) 공휴일도 아니면 거래일 → 종가 조회
                    previousPrice = stockPriceRepository
                        .findByStockCodeAndPriceDate(stockCode, searchDate);
                    
                    if (!previousPrice.isPresent()) {
                        searchDate = searchDate.minusDays(1);
                        searchDays++;
                    }
                }
            }
            
            // 오늘 종가 조회
            Optional<StockPrice> todayPrice = stockPriceRepository
                .findByStockCodeAndPriceDate(stockCode, date);
            
            // 평단가 계산 (현지 통화 기준으로 변환 필요)
            // HoldingInfo의 averagePrice는 매수 기록의 totalAmount를 합산한 것인데,
            // totalAmount는 현지 통화 기준이므로, 원화로 변환해야 함
            BigDecimal avgPriceInStockCurrency = holding.getAveragePrice(); // 현지 통화 기준 평단가
            BigDecimal holdingQuantity = holding.getHoldingQuantity();
            
            // 종목의 통화 확인 (오늘 종가가 있으면 그 통화 사용, 없으면 첫 매수 기록의 통화 추정)
            String stockCurrency = "KRW";
            if (todayPrice.isPresent()) {
                stockCurrency = todayPrice.get().getCurrency();
            } else if (firstBuyRecordOpt.isPresent()) {
                // 첫 매수 기록에서 통화 추정
                InvestmentRecord firstBuyRecord = firstBuyRecordOpt.get();
                String stockCodeFromRecord = firstBuyRecord.getStockCode();
                if (stockCodeFromRecord != null && !isKoreanStock(stockCodeFromRecord)) {
                    stockCurrency = "USD";
                }
            }
            
            // 평단가를 원화로 변환
            BigDecimal exchangeRateForAvgPrice = getExchangeRate(stockCurrency, userCurrency, date);
            BigDecimal avgPriceInUserCurrency = avgPriceInStockCurrency.multiply(exchangeRateForAvgPrice);
            
            Map<String, Object> stockDetail = new HashMap<>();
            stockDetail.put("stockCode", stockCode);
            stockDetail.put("stockName", stockName);
            stockDetail.put("holdingQuantity", holdingQuantity);
            
            // 오늘 종가가 있으면 최종 수익률 계산 가능
            if (todayPrice.isPresent()) {
                BigDecimal todayClosePrice = todayPrice.get().getClosePrice();
                String actualStockCurrency = todayPrice.get().getCurrency(); // 실제 종가의 통화
                
                // 종가 정보 추가 (현지 통화 기준)
                stockDetail.put("currentPrice", todayClosePrice);
                stockDetail.put("currency", actualStockCurrency);
                
                // 환율 적용 (실제 종가의 통화 기준)
                BigDecimal exchangeRate = getExchangeRate(actualStockCurrency, userCurrency, date);
                BigDecimal todayPriceInUserCurrency = todayClosePrice.multiply(exchangeRate);
                
                // 환율 정보 추가 (정렬 시 사용)
                stockDetail.put("exchangeRate", exchangeRate);
                
                // 평단가를 실제 종가의 통화로 변환 (표시용)
                // avgPriceInStockCurrency는 추정된 통화 기준이므로, 실제 종가 통화로 다시 변환
                BigDecimal avgPriceInActualCurrency;
                if (!actualStockCurrency.equals(stockCurrency)) {
                    // 통화가 다르면 환율로 변환
                    BigDecimal conversionRate = getExchangeRate(stockCurrency, actualStockCurrency, date);
                    avgPriceInActualCurrency = avgPriceInStockCurrency.multiply(conversionRate);
                } else {
                    avgPriceInActualCurrency = avgPriceInStockCurrency;
                }
                stockDetail.put("averagePrice", avgPriceInActualCurrency);
                
                // 평단가를 원화로 다시 계산 (실제 종가 통화 기준)
                BigDecimal avgPriceInUserCurrencyActual = avgPriceInActualCurrency.multiply(exchangeRate);
                
                // 오늘 평단가 기준 수익률 (원화 기준으로 계산)
                BigDecimal todayProfitRate = BigDecimal.ZERO;
                if (avgPriceInUserCurrencyActual.compareTo(BigDecimal.ZERO) > 0) {
                    todayProfitRate = todayPriceInUserCurrency
                        .subtract(avgPriceInUserCurrencyActual)
                        .divide(avgPriceInUserCurrencyActual, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                }
                
                // 수익금 계산 (현지 통화 기준 - 표시용, 정렬 시 프론트엔드에서 환율 적용)
                BigDecimal profitAmount = todayClosePrice
                    .subtract(avgPriceInActualCurrency)
                    .multiply(holdingQuantity);
                
                // 전날 종가가 있으면 전날 수익률과 수익률 변화량 계산
                BigDecimal previousProfitRate = BigDecimal.ZERO;
                BigDecimal profitRateChange = BigDecimal.ZERO;
                
                if (previousPrice.isPresent()) {
                    BigDecimal previousClosePrice = previousPrice.get().getClosePrice();
                    BigDecimal previousPriceInUserCurrency = previousClosePrice.multiply(exchangeRate);
                    
                    // 전날 평단가 기준 수익률 (원화 기준)
                    if (avgPriceInUserCurrencyActual.compareTo(BigDecimal.ZERO) > 0) {
                        previousProfitRate = previousPriceInUserCurrency
                            .subtract(avgPriceInUserCurrencyActual)
                            .divide(avgPriceInUserCurrencyActual, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    }
                    
                    // 수익률 변화량 계산
                    // 주말이거나 전날 종가가 없으면 0
                    // 한국 주식이고 한국 공휴일이면 0
                    boolean isKorean = isKoreanStock(stockCode);
                    boolean isHoliday = isKorean && isKoreanHoliday(date);
                    
                    if (isWeekend || isHoliday) {
                        profitRateChange = BigDecimal.ZERO;
                    } else {
                        profitRateChange = todayProfitRate.subtract(previousProfitRate);
                    }
                } else {
                    // 전날 종가가 없으면 수익률 변화량은 0
                    profitRateChange = BigDecimal.ZERO;
                }
                
                stockDetail.put("previousProfitRate", previousProfitRate);
                stockDetail.put("todayProfitRate", todayProfitRate);
                stockDetail.put("profitRateChange", profitRateChange);
                stockDetail.put("profitAmount", profitAmount);
            } else {
                // 오늘 종가도 없으면 모든 값 0
                // 평단가는 현지 통화 기준으로 표시 (stockCurrency가 "KRW"로 설정되어 있으면 그대로, "USD"면 USD로)
                stockDetail.put("averagePrice", avgPriceInStockCurrency);
                stockDetail.put("currentPrice", BigDecimal.ZERO);
                stockDetail.put("currency", stockCurrency);
                stockDetail.put("exchangeRate", exchangeRateForAvgPrice); // 환율 정보 추가
                stockDetail.put("previousProfitRate", BigDecimal.ZERO);
                stockDetail.put("todayProfitRate", BigDecimal.ZERO);
                stockDetail.put("profitRateChange", BigDecimal.ZERO);
                stockDetail.put("profitAmount", BigDecimal.ZERO);
            }
            
            stockDetails.add(stockDetail);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("date", date.toString());
        result.put("stocks", stockDetails);
        
        return ApiResponse.success(result, "해당 날짜의 보유 종목 정보를 조회했습니다.");
    }
    
    /**
     * 캘린더 일자 데이터 생성
     */
    private List<Map<String, Object>> createCalendarDays(YearMonth yearMonth, 
                                                         Map<LocalDate, List<InvestmentRecord>> recordsByDate,
                                                         Long userId) {
        List<Map<String, Object>> calendarDays = new ArrayList<>();
        
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            List<InvestmentRecord> dayRecords = recordsByDate.getOrDefault(currentDate, new ArrayList<>());
            
            Map<String, Object> dayData = createDayData(currentDate, dayRecords, userId);
            calendarDays.add(dayData);
            
            currentDate = currentDate.plusDays(1);
        }
        
        return calendarDays;
    }
    
    /**
     * 캘린더 일자 데이터 생성 (캐시 사용 - 성능 최적화)
     */
    private List<Map<String, Object>> createCalendarDaysCached(YearMonth yearMonth, 
                                                                Map<LocalDate, List<InvestmentRecord>> recordsByDate,
                                                                Long userId, CalendarCacheData cache) {
        List<Map<String, Object>> calendarDays = new ArrayList<>();
        
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            List<InvestmentRecord> dayRecords = recordsByDate.getOrDefault(currentDate, new ArrayList<>());
            
            Map<String, Object> dayData = createDayDataCached(currentDate, dayRecords, userId, cache);
            calendarDays.add(dayData);
            
            currentDate = currentDate.plusDays(1);
        }
        
        return calendarDays;
    }
    
    /**
     * 일자별 데이터 생성
     * 전날 대비 평단가 기준 수익률 변화량 계산
     */
    private Map<String, Object> createDayData(LocalDate date, List<InvestmentRecord> records, Long userId) {
        Map<String, Object> dayData = new HashMap<>();
        dayData.put("date", date.toString());
        dayData.put("hasInvestment", !records.isEmpty());
        dayData.put("recordCount", records.size());
        
        // 총 투자 금액 계산 (해당 날짜의 투자 기록만)
        BigDecimal totalInvestment = BigDecimal.ZERO;
        if (!records.isEmpty()) {
            totalInvestment = records.stream()
                .filter(record -> record.getTotalAmount() != null)
                .map(InvestmentRecord::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        dayData.put("totalInvestment", totalInvestment);
        
        // 전날 대비 평단가 기준 수익률 변화량 계산
        // 해당 날짜에 투자 기록이 없어도 보유 종목이 있으면 계산
        Map<String, Object> profitRateInfo = calculateDailyProfitRateChangeWithInfo(date, userId);
        dayData.put("profitRateChange", profitRateInfo.get("profitRateChange"));
        dayData.put("profitRate", profitRateInfo.get("profitRate")); // 오늘 수익률 추가
        // 공휴일 정보는 calculateDailyProfitRateChangeWithInfo에서 정확하게 계산됨
        // (한국 주식이 모두 공휴일인 경우에만 공휴일로 판단)
        dayData.put("isHoliday", profitRateInfo.get("isHoliday")); 
        dayData.put("canCalculate", profitRateInfo.get("canCalculate")); // 계산 가능 여부
        
        return dayData;
    }
    
    /**
     * 일자별 데이터 생성 (캐시 사용 - 성능 최적화)
     */
    private Map<String, Object> createDayDataCached(LocalDate date, List<InvestmentRecord> records, 
                                                     Long userId, CalendarCacheData cache) {
        Map<String, Object> dayData = new HashMap<>();
        dayData.put("date", date.toString());
        dayData.put("hasInvestment", !records.isEmpty());
        dayData.put("recordCount", records.size());
        
        // 총 투자 금액 계산 (해당 날짜의 투자 기록만)
        BigDecimal totalInvestment = BigDecimal.ZERO;
        if (!records.isEmpty()) {
            totalInvestment = records.stream()
                .filter(record -> record.getTotalAmount() != null)
                .map(InvestmentRecord::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        dayData.put("totalInvestment", totalInvestment);
        
        // 전날 대비 평단가 기준 수익률 변화량 계산 (캐시 사용)
        Map<String, Object> profitRateInfo = calculateDailyProfitRateChangeWithInfoCached(date, userId, cache);
        dayData.put("profitRateChange", profitRateInfo.get("profitRateChange"));
        dayData.put("profitRate", profitRateInfo.get("profitRate"));
        dayData.put("isHoliday", profitRateInfo.get("isHoliday")); 
        dayData.put("canCalculate", profitRateInfo.get("canCalculate"));
        
        return dayData;
    }
    
    /**
     * 전날 대비 평단가 기준 수익률 변화량 계산 (캐시 사용 - 성능 최적화)
     * DB 쿼리 없이 사전 로딩된 데이터로 계산
     */
    private Map<String, Object> calculateDailyProfitRateChangeWithInfoCached(LocalDate date, Long userId, CalendarCacheData cache) {
        Map<String, Object> result = new HashMap<>();
        
        // 보유 종목 계산 (캐시에서 메모리 필터링)
        Map<String, HoldingInfo> holdings = cache.getHoldingsAsOf(date.minusDays(1), date);
        
        // 주말/공휴일 확인
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY);
        boolean isHoliday = isKoreanHoliday(date);
        
        // 보유 종목이 없으면 계산 불가
        if (holdings.isEmpty()) {
            result.put("profitRateChange", 0.0);
            result.put("profitRate", 0.0);
            result.put("isHoliday", isHoliday && !isWeekend);
            result.put("canCalculate", false);
            return result;
        }
        
        // 주말이면 계산하지 않음
        if (isWeekend) {
            result.put("profitRateChange", 0.0);
            result.put("profitRate", 0.0);
            result.put("isHoliday", false);
            result.put("canCalculate", false);
            return result;
        }
        
        // 전날 영업일 계산
        LocalDate previousDate = getPreviousTradingDate(date);
        BigDecimal totalProfitRateChange = BigDecimal.ZERO;
        BigDecimal totalTodayProfitRate = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        int stocksWithBothPrices = 0;
        int koreanStocksOnHoliday = 0;
        int totalKoreanStocks = 0;
        
        for (Map.Entry<String, HoldingInfo> entry : holdings.entrySet()) {
            String stockCode = entry.getKey();
            HoldingInfo holding = entry.getValue();
            
            // 한국 주식인지 확인 (캐시 사용)
            boolean isKorean = cache.isKoreanStock(stockCode);
            if (isKorean) {
                totalKoreanStocks++;
                if (isHoliday) {
                    koreanStocksOnHoliday++;
                    continue;
                }
            }
            
            // 종가 조회 (캐시 사용 - DB 쿼리 없음)
            Optional<StockPrice> previousPrice = cache.getStockPrice(stockCode, previousDate);
            Optional<StockPrice> todayPrice = cache.getStockPrice(stockCode, date);
            
            boolean hasPrevious = previousPrice.isPresent();
            boolean hasToday = todayPrice.isPresent();
            
            // 전날 종가가 없고 오늘 종가만 있는 경우: 이전 거래일 탐색 (캐시에서)
            if (!hasPrevious && hasToday) {
                LocalDate searchDate = date.minusDays(1);
                int searchDays = 0;
                int maxSearchDays = 10;
                
                while (searchDays < maxSearchDays && !hasPrevious) {
                    java.time.DayOfWeek searchDayOfWeek = searchDate.getDayOfWeek();
                    boolean isSearchWeekend = (searchDayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                              searchDayOfWeek == java.time.DayOfWeek.SUNDAY);
                    boolean isSearchHoliday = isKorean && isKoreanHoliday(searchDate);
                    
                    if (!isSearchWeekend && !isSearchHoliday) {
                        Optional<StockPrice> found = cache.getStockPrice(stockCode, searchDate);
                        if (found.isPresent()) {
                            previousPrice = found;
                            hasPrevious = true;
                            stocksWithBothPrices++;
                        }
                    }
                    
                    searchDate = searchDate.minusDays(1);
                    searchDays++;
                }
                
                if (!hasPrevious) continue;
            } else if (hasPrevious && hasToday) {
                stocksWithBothPrices++;
            }
            
            if (!hasPrevious || !hasToday) continue;
            
            BigDecimal previousClosePrice = previousPrice.get().getClosePrice();
            BigDecimal todayClosePrice = todayPrice.get().getClosePrice();
            String stockCurrency = previousPrice.get().getCurrency();
            
            // 환율 적용 (캐시 사용)
            BigDecimal exchangeRate = cache.getExchangeRate(stockCurrency, cache.userCurrency);
            BigDecimal previousPriceInUserCurrency = previousClosePrice.multiply(exchangeRate);
            BigDecimal todayPriceInUserCurrency = todayClosePrice.multiply(exchangeRate);
            
            // 평단가 기준 수익률 계산
            BigDecimal avgPrice = holding.getAveragePrice();
            if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) == 0) continue;
            
            BigDecimal avgPriceInUserCurrency = avgPrice.multiply(exchangeRate);
            
            // 전날 수익률
            BigDecimal previousProfitRate = previousPriceInUserCurrency
                .subtract(avgPriceInUserCurrency)
                .divide(avgPriceInUserCurrency, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // 오늘 수익률
            BigDecimal todayProfitRate = todayPriceInUserCurrency
                .subtract(avgPriceInUserCurrency)
                .divide(avgPriceInUserCurrency, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // 수익률 변화량
            BigDecimal profitRateChange = todayProfitRate.subtract(previousProfitRate);
            
            // 가중치 (보유 수량 × 평단가, 원화 기준)
            BigDecimal weight = holding.getHoldingQuantity().multiply(avgPriceInUserCurrency);
            
            totalProfitRateChange = totalProfitRateChange.add(profitRateChange.multiply(weight));
            totalTodayProfitRate = totalTodayProfitRate.add(todayProfitRate.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            boolean allKoreanStocksOnHoliday = (totalKoreanStocks > 0) && 
                                               (koreanStocksOnHoliday == totalKoreanStocks) &&
                                               (stocksWithBothPrices == 0);
            result.put("profitRateChange", 0.0);
            result.put("profitRate", 0.0);
            result.put("isHoliday", allKoreanStocksOnHoliday);
            result.put("canCalculate", false);
            return result;
        }
        
        double profitRateChange = totalProfitRateChange
            .divide(totalWeight, 4, RoundingMode.HALF_UP)
            .doubleValue();
        
        double todayProfitRate = totalTodayProfitRate
            .divide(totalWeight, 4, RoundingMode.HALF_UP)
            .doubleValue();
        
        boolean allKoreanStocksOnHoliday = (totalKoreanStocks > 0) && 
                                           (koreanStocksOnHoliday == totalKoreanStocks) &&
                                           (stocksWithBothPrices == 0);
        
        result.put("profitRateChange", Math.round(profitRateChange * 100.0) / 100.0);
        result.put("profitRate", Math.round(todayProfitRate * 100.0) / 100.0);
        result.put("isHoliday", allKoreanStocksOnHoliday);
        result.put("canCalculate", true);
        return result;
    }
    
    /**
     * 일일 수익률 계산 (기존 메서드 - 호환성 유지)
     */
    private double calculateDailyProfitRate(List<InvestmentRecord> records) {
        if (records.isEmpty()) {
            return 0.0;
        }
        
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalInvestment = BigDecimal.ZERO;
        
        for (InvestmentRecord record : records) {
            if (record.getTotalAmount() != null) {
                totalInvestment = totalInvestment.add(record.getTotalAmount());
            }
            
            if (record.getUnrealizedProfitAmount() != null) {
                totalProfit = totalProfit.add(record.getUnrealizedProfitAmount());
            }
        }
        
        if (totalInvestment.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        return totalProfit
            .divide(totalInvestment, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .doubleValue();
    }
    
    /**
     * 전날 대비 평단가 기준 수익률 변화량 계산 (정보 포함)
     * 공휴일 여부와 계산 가능 여부를 함께 반환
     */
    private Map<String, Object> calculateDailyProfitRateChangeWithInfo(LocalDate date, Long userId) {
        Map<String, Object> result = new HashMap<>();
        
        // 해당 날짜 이전까지의 모든 매수 기록 조회 (보유 종목 계산용)
        List<InvestmentRecord> allBuyRecords = investmentRecordRepository
            .findRecordsByDateRange(userId, LocalDate.of(1900, 1, 1), date.minusDays(1));
        
        // 보유 종목 계산 (매수 수량 - 매도 수량)
        Map<String, HoldingInfo> holdings = new HashMap<>();
        
        for (InvestmentRecord record : allBuyRecords) {
            // 외환 기록은 일일 포트폴리오에서 제외
            if (record.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (record.getType() != InvestmentRecord.InvestmentType.BUY || 
                record.getStockCode() == null || record.getQuantity() == null) {
                continue;
            }
            
            String stockCode = record.getStockCode();
            HoldingInfo holding = holdings.getOrDefault(stockCode, new HoldingInfo());
            holding.addBuyRecord(record);
            holdings.put(stockCode, holding);
        }
        
        // 해당 날짜까지의 매도 기록으로 보유 수량 조정
        List<InvestmentRecord> sellRecords = investmentRecordRepository
            .findRecordsByDateRange(userId, LocalDate.of(1900, 1, 1), date);
        
        for (InvestmentRecord sellRecord : sellRecords) {
            // 외환 기록은 일일 포트폴리오에서 제외
            if (sellRecord.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (sellRecord.getType() != InvestmentRecord.InvestmentType.SELL ||
                sellRecord.getStockCode() == null || sellRecord.getSellQuantity() == null) {
                continue;
            }
            
            String stockCode = sellRecord.getStockCode();
            if (holdings.containsKey(stockCode)) {
                holdings.get(stockCode).subtractSellQuantity(sellRecord.getSellQuantity());
            }
        }
        
        // 보유 수량이 0보다 큰 종목만 필터링
        holdings.entrySet().removeIf(entry -> entry.getValue().getHoldingQuantity().compareTo(BigDecimal.ZERO) <= 0);
        
        // 해당 날짜가 주말인지 확인
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY);
        boolean isHoliday = isKoreanHoliday(date);
        
        // 보유 종목이 없으면 계산 불가
        if (holdings.isEmpty()) {
            result.put("profitRateChange", 0.0);
            result.put("isHoliday", isHoliday && !isWeekend); // 평일 중 공휴일만 표시
            result.put("canCalculate", false);
            return result;
        }
        
        // 주말이면 수익률 변화량 계산하지 않음 (주말은 공휴일로 표시하지 않음)
        if (isWeekend) {
            result.put("profitRateChange", 0.0);
            result.put("isHoliday", false); // 주말은 공휴일로 표시하지 않음
            result.put("canCalculate", false);
            return result;
        }
        
        // 공휴일이면 수익률 변화량 계산하지 않음
        // 단, 한국 주식만 공휴일이고 미국 주식이 있으면 계산 가능
        // 따라서 여기서는 바로 return하지 않고, 아래에서 종목별로 처리
        
        // 사용자의 기본 통화 가져오기
        String userCurrency = portfolioSettingsRepository.findByUserId(userId)
            .map(PortfolioSettings::getCurrency)
            .orElse("KRW");
        
        // 전날 영업일 계산 (주말 및 공휴일 건너뛰기)
        LocalDate previousDate = getPreviousTradingDate(date);
        BigDecimal totalProfitRateChange = BigDecimal.ZERO;
        BigDecimal totalTodayProfitRate = BigDecimal.ZERO; // 오늘 수익률 (가중 평균)
        BigDecimal totalWeight = BigDecimal.ZERO;
        int stocksWithBothPrices = 0; // 전날 종가와 오늘 종가가 모두 있는 종목 수
        int stocksWithOnlyTodayPrice = 0; // 오늘 종가만 있는 종목 수 (전날이 공휴일)
        int stocksWithOnlyPreviousPrice = 0; // 전날 종가만 있는 종목 수 (오늘이 공휴일 가능성)
        int stocksWithNoPrice = 0; // 둘 다 없는 종목 수
        int koreanStocksOnHoliday = 0; // 한국 주식 중 오늘이 공휴일인 종목 수
        int totalKoreanStocks = 0; // 전체 한국 주식 수
        
        for (Map.Entry<String, HoldingInfo> entry : holdings.entrySet()) {
            String stockCode = entry.getKey();
            HoldingInfo holding = entry.getValue();
            
            // 한국 주식인지 확인
            boolean isKorean = isKoreanStock(stockCode);
            if (isKorean) {
                totalKoreanStocks++;
                // 한국 주식이고 오늘이 공휴일이면 계산하지 않음
                if (isHoliday) {
                    koreanStocksOnHoliday++;
                    continue;
                }
            }
            
            // 전날 종가 조회 (영업일 기준)
            Optional<StockPrice> previousPrice = stockPriceRepository
                .findByStockCodeAndPriceDate(stockCode, previousDate);
            
            // 오늘 종가 조회
            Optional<StockPrice> todayPrice = stockPriceRepository
                .findByStockCodeAndPriceDate(stockCode, date);
            
            boolean hasPrevious = previousPrice.isPresent();
            boolean hasToday = todayPrice.isPresent();
            
            // 종가 존재 여부에 따라 분류
            if (hasPrevious && hasToday) {
                stocksWithBothPrices++;
            } else if (!hasPrevious && hasToday) {
                stocksWithOnlyTodayPrice++; // 전날이 공휴일이었을 가능성
            } else if (hasPrevious && !hasToday) {
                stocksWithOnlyPreviousPrice++; // 오늘이 공휴일 가능성 (한국 주식인 경우)
            } else {
                stocksWithNoPrice++; // 데이터 부족
            }
            
            // 전날 종가가 없고 오늘 종가만 있는 경우: 전날이 공휴일이었을 가능성
            // → 공휴일 전 마지막 거래일을 찾아서 비교
            if (!hasPrevious && hasToday) {
                // 공휴일 전 마지막 거래일 찾기 (직접 탐색, 무한 루프 방지)
                LocalDate searchDate = date.minusDays(1);
                Optional<StockPrice> lastTradingPrice = Optional.empty();
                int searchDays = 0;
                int maxSearchDays = 10; // 최대 10일 전까지 검색
                
                while (searchDays < maxSearchDays && !lastTradingPrice.isPresent()) {
                    java.time.DayOfWeek searchDayOfWeek = searchDate.getDayOfWeek();
                    boolean isSearchWeekend = (searchDayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                              searchDayOfWeek == java.time.DayOfWeek.SUNDAY);
                    // 한국 주식인 경우에만 공휴일 체크
                    boolean isSearchHoliday = isKorean && isKoreanHoliday(searchDate);
                    
                    // 주말도 아니고 (한국 주식인 경우) 공휴일도 아니면 거래일
                    if (!isSearchWeekend && !isSearchHoliday) {
                        lastTradingPrice = stockPriceRepository
                            .findByStockCodeAndPriceDate(stockCode, searchDate);
                    }
                    
                    searchDate = searchDate.minusDays(1);
                    searchDays++;
                }
                
                if (lastTradingPrice.isPresent()) {
                    // 공휴일 전 마지막 거래일 종가를 전날 종가로 사용
                    previousPrice = lastTradingPrice;
                    hasPrevious = true;
                    stocksWithOnlyTodayPrice--;
                    stocksWithBothPrices++;
                } else {
                    continue; // 여전히 전날 종가를 찾을 수 없으면 스킵
                }
            }
            
            // 전날 종가와 오늘 종가가 모두 있어야 계산 가능
            if (!hasPrevious || !hasToday) {
                continue;
            }
            
            BigDecimal previousClosePrice = previousPrice.get().getClosePrice();
            BigDecimal todayClosePrice = todayPrice.get().getClosePrice();
            String stockCurrency = previousPrice.get().getCurrency();
            
            // 환율 적용
            BigDecimal exchangeRate = getExchangeRate(stockCurrency, userCurrency, date);
            BigDecimal previousPriceInUserCurrency = previousClosePrice.multiply(exchangeRate);
            BigDecimal todayPriceInUserCurrency = todayClosePrice.multiply(exchangeRate);
            
            // 평단가 기준 수익률 계산
            BigDecimal avgPrice = holding.getAveragePrice(); // 현지 통화 기준 평단가
            if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            
            // 평단가도 원화로 변환 (종가와 동일한 통화로 비교)
            BigDecimal avgPriceInUserCurrency = avgPrice.multiply(exchangeRate);
            
            // 전날 평단가 기준 수익률 (원화 기준)
            BigDecimal previousProfitRate = previousPriceInUserCurrency
                .subtract(avgPriceInUserCurrency)
                .divide(avgPriceInUserCurrency, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // 오늘 평단가 기준 수익률 (원화 기준)
            BigDecimal todayProfitRate = todayPriceInUserCurrency
                .subtract(avgPriceInUserCurrency)
                .divide(avgPriceInUserCurrency, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // 수익률 변화량
            BigDecimal profitRateChange = todayProfitRate.subtract(previousProfitRate);
            
            // 가중치 (보유 수량 × 평단가, 원화 기준으로 통일)
            BigDecimal weight = holding.getHoldingQuantity()
                .multiply(avgPriceInUserCurrency);
            
            totalProfitRateChange = totalProfitRateChange.add(profitRateChange.multiply(weight));
            totalTodayProfitRate = totalTodayProfitRate.add(todayProfitRate.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        
        // 가중치가 0이면 계산 불가
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            // 한국 주식이 모두 공휴일이면 공휴일로 표시
            // 일부만 공휴일이거나 미국 주식이 있으면 공휴일이 아님
            boolean allKoreanStocksOnHoliday = (totalKoreanStocks > 0) && 
                                               (koreanStocksOnHoliday == totalKoreanStocks) &&
                                               (stocksWithBothPrices == 0);
            result.put("profitRateChange", 0.0);
            result.put("profitRate", 0.0); // 수익률도 0으로 설정
            result.put("isHoliday", allKoreanStocksOnHoliday);
            result.put("canCalculate", false);
            return result;
        }
        
        // 가중 평균 수익률 변화량
        double profitRateChange = totalProfitRateChange
            .divide(totalWeight, 4, RoundingMode.HALF_UP)
            .doubleValue();
        
        // 가중 평균 오늘 수익률 (월별/연도별 계산용)
        double todayProfitRate = totalTodayProfitRate
            .divide(totalWeight, 4, RoundingMode.HALF_UP)
            .doubleValue();
        
        // 한국 주식이 모두 공휴일이면 공휴일로 표시
        // 일부만 공휴일이거나 미국 주식이 있어서 계산 가능하면 공휴일이 아님
        boolean allKoreanStocksOnHoliday = (totalKoreanStocks > 0) && 
                                           (koreanStocksOnHoliday == totalKoreanStocks) &&
                                           (stocksWithBothPrices == 0);
        
        result.put("profitRateChange", Math.round(profitRateChange * 100.0) / 100.0);
        result.put("profitRate", Math.round(todayProfitRate * 100.0) / 100.0); // 오늘 수익률 추가
        result.put("isHoliday", allKoreanStocksOnHoliday);
        result.put("canCalculate", true);
        return result;
    }
    
    /**
     * 전날 대비 평단가 기준 수익률 변화량 계산 (기존 메서드 - 호환성 유지)
     * 특정 날짜의 보유 종목들의 전날 종가 대비 오늘 종가 기준 수익률 변화량을 계산
     */
    private double calculateDailyProfitRateChange(LocalDate date, Long userId) {
        // 해당 날짜 이전까지의 모든 매수 기록 조회 (보유 종목 계산용)
        List<InvestmentRecord> allBuyRecords = investmentRecordRepository
            .findRecordsByDateRange(userId, LocalDate.of(1900, 1, 1), date.minusDays(1));
        
        // 보유 종목 계산 (매수 수량 - 매도 수량)
        Map<String, HoldingInfo> holdings = new HashMap<>();
        
        for (InvestmentRecord record : allBuyRecords) {
            // 외환 기록은 일일 포트폴리오에서 제외
            if (record.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (record.getType() != InvestmentRecord.InvestmentType.BUY || 
                record.getStockCode() == null || record.getQuantity() == null) {
                continue;
            }
            
            String stockCode = record.getStockCode();
            HoldingInfo holding = holdings.getOrDefault(stockCode, new HoldingInfo());
            holding.addBuyRecord(record);
            holdings.put(stockCode, holding);
        }
        
        // 해당 날짜까지의 매도 기록으로 보유 수량 조정
        List<InvestmentRecord> sellRecords = investmentRecordRepository
            .findRecordsByDateRange(userId, LocalDate.of(1900, 1, 1), date);
        
        for (InvestmentRecord sellRecord : sellRecords) {
            // 외환 기록은 일일 포트폴리오에서 제외
            if (sellRecord.getAssetType() == AssetType.CURRENCY) {
                continue;
            }
            
            if (sellRecord.getType() != InvestmentRecord.InvestmentType.SELL ||
                sellRecord.getStockCode() == null || sellRecord.getSellQuantity() == null) {
                continue;
            }
            
            String stockCode = sellRecord.getStockCode();
            if (holdings.containsKey(stockCode)) {
                holdings.get(stockCode).subtractSellQuantity(sellRecord.getSellQuantity());
            }
        }
        
        // 보유 수량이 0보다 큰 종목만 필터링
        holdings.entrySet().removeIf(entry -> entry.getValue().getHoldingQuantity().compareTo(BigDecimal.ZERO) <= 0);
        
        if (holdings.isEmpty()) {
            return 0.0;
        }
        
        // 사용자의 기본 통화 가져오기
        String userCurrency = portfolioSettingsRepository.findByUserId(userId)
            .map(PortfolioSettings::getCurrency)
            .orElse("KRW");
        
        // 해당 날짜가 주말인지 확인
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY);
        boolean isHoliday = isKoreanHoliday(date);
        
        // 주말이면 수익률 변화량 계산하지 않음 (한국 시간 기준)
        // 토요일: 미국 금요일 장 마감 후이지만, 월요일에 표시하는 것이 더 자연스러움
        // 일요일: 장 없음
        if (isWeekend) {
            return 0.0;
        }
        
        // 공휴일이면 수익률 변화량 계산하지 않음
        if (isHoliday) {
            return 0.0;
        }
        
        // 전날 영업일 계산 (주말 및 공휴일 건너뛰기)
        // 월요일의 전일 = 금요일
        // 공휴일 다음날의 전일 = 공휴일 전 마지막 거래일
        LocalDate previousDate = getPreviousTradingDate(date);
        BigDecimal totalProfitRateChange = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        
        for (Map.Entry<String, HoldingInfo> entry : holdings.entrySet()) {
            String stockCode = entry.getKey();
            HoldingInfo holding = entry.getValue();
            
            // 전날 종가 조회 (영업일 기준)
            Optional<StockPrice> previousPrice = stockPriceRepository
                .findByStockCodeAndPriceDate(stockCode, previousDate);
            
            // 오늘 종가 조회
            Optional<StockPrice> todayPrice = stockPriceRepository
                .findByStockCodeAndPriceDate(stockCode, date);
            
            // 전날 종가가 없고 오늘 종가만 있는 경우: 전날이 공휴일이었을 가능성
            // → 공휴일 전 마지막 거래일을 찾아서 비교
            if (!previousPrice.isPresent() && todayPrice.isPresent()) {
                // 공휴일 전 마지막 거래일 찾기 (직접 탐색, 무한 루프 방지)
                LocalDate searchDate = date.minusDays(1);
                Optional<StockPrice> lastTradingPrice = Optional.empty();
                int searchDays = 0;
                int maxSearchDays = 10; // 최대 10일 전까지 검색
                
                while (searchDays < maxSearchDays && !lastTradingPrice.isPresent()) {
                    java.time.DayOfWeek searchDayOfWeek = searchDate.getDayOfWeek();
                    boolean isSearchWeekend = (searchDayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                              searchDayOfWeek == java.time.DayOfWeek.SUNDAY);
                    boolean isSearchHoliday = isKoreanHoliday(searchDate);
                    
                    // 주말도 아니고 공휴일도 아니면 거래일
                    if (!isSearchWeekend && !isSearchHoliday) {
                        lastTradingPrice = stockPriceRepository
                            .findByStockCodeAndPriceDate(stockCode, searchDate);
                    }
                    
                    searchDate = searchDate.minusDays(1);
                    searchDays++;
                }
                
                if (lastTradingPrice.isPresent()) {
                    previousPrice = lastTradingPrice;
                }
            }
            
            if (!previousPrice.isPresent() || !todayPrice.isPresent()) {
                continue;
            }
            
            BigDecimal previousClosePrice = previousPrice.get().getClosePrice();
            BigDecimal todayClosePrice = todayPrice.get().getClosePrice();
            String stockCurrency = previousPrice.get().getCurrency();
            
            // 환율 적용
            BigDecimal exchangeRate = getExchangeRate(stockCurrency, userCurrency, date);
            BigDecimal previousPriceInUserCurrency = previousClosePrice.multiply(exchangeRate);
            BigDecimal todayPriceInUserCurrency = todayClosePrice.multiply(exchangeRate);
            
            // 평단가 기준 수익률 계산
            BigDecimal avgPrice = holding.getAveragePrice(); // 현지 통화 기준 평단가
            if (avgPrice == null || avgPrice.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            
            // 평단가도 원화로 변환 (종가와 동일한 통화로 비교)
            BigDecimal avgPriceInUserCurrency = avgPrice.multiply(exchangeRate);
            
            // 전날 평단가 기준 수익률 (원화 기준)
            BigDecimal previousProfitRate = previousPriceInUserCurrency
                .subtract(avgPriceInUserCurrency)
                .divide(avgPriceInUserCurrency, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // 오늘 평단가 기준 수익률 (원화 기준)
            BigDecimal todayProfitRate = todayPriceInUserCurrency
                .subtract(avgPriceInUserCurrency)
                .divide(avgPriceInUserCurrency, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
            
            // 수익률 변화량
            BigDecimal profitRateChange = todayProfitRate.subtract(previousProfitRate);
            
            // 가중치 (보유 수량 × 평단가, 원화 기준으로 통일)
            BigDecimal weight = holding.getHoldingQuantity()
                .multiply(avgPriceInUserCurrency);
            
            totalProfitRateChange = totalProfitRateChange.add(profitRateChange.multiply(weight));
            totalWeight = totalWeight.add(weight);
        }
        
        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        // 가중 평균 수익률 변화량
        return totalProfitRateChange
            .divide(totalWeight, 4, RoundingMode.HALF_UP)
            .doubleValue();
    }
    
    /**
     * 종목코드가 한국 주식인지 확인
     */
    private boolean isKoreanStock(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) {
            return false;
        }
        
        // 티커 매핑에서 국가 정보 확인
        Optional<StockTickerMapping> mapping = tickerMappingRepository.findByStockCode(stockCode);
        if (mapping.isPresent()) {
            String country = mapping.get().getCountry();
            return "KR".equals(country);
        }
        
        // 매핑이 없으면 stockCode 형식으로 판단
        // 숫자로만 구성되어 있으면 한국 주식
        if (stockCode.matches("^[0-9]+$")) {
            return true;
        }
        
        // .KS 또는 .KQ로 끝나면 한국 주식
        if (stockCode.endsWith(".KS") || stockCode.endsWith(".KQ")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 한국 공휴일인지 확인
     */
    private boolean isKoreanHoliday(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        
        // 고정 공휴일
        if (month == 1 && day == 1) return true; // 신정
        if (month == 3 && day == 1) return true; // 삼일절
        if (month == 5 && day == 5) return true; // 어린이날
        if (month == 6 && day == 6) return true; // 현충일
        if (month == 8 && day == 15) return true; // 광복절
        if (month == 10 && day == 3) return true; // 개천절
        if (month == 10 && day == 9) return true; // 한글날
        if (month == 12 && day == 25) return true; // 크리스마스
        
        // 특정 연도의 선거일
        if (year == 2022 && month == 3 && day == 9) return true; // 제20대 대통령선거
        if (year == 2024 && month == 4 && day == 10) return true; // 제22대 국회의원선거
        
        // 설날 연휴 (음력 1월 1일 전후, 양력으로 근사치 계산)
        // 2022년 설날: 2월 1일 (음력 1월 1일), 연휴: 1월 31일, 2월 1일, 2월 2일
        if (year == 2022 && month == 1 && day == 31) return true;
        if (year == 2022 && month == 2 && day == 1) return true;
        if (year == 2022 && month == 2 && day == 2) return true;
        
        // 2023년 설날: 1월 22일 (음력 1월 1일), 연휴: 1월 21일, 1월 22일, 1월 23일
        if (year == 2023 && month == 1 && day >= 21 && day <= 23) return true;
        
        // 2024년 설날: 2월 10일 (음력 1월 1일), 연휴: 2월 9일, 2월 10일, 2월 11일
        if (year == 2024 && month == 2 && day >= 9 && day <= 11) return true;
        
        // 2025년 설날: 1월 29일 (음력 1월 1일), 연휴: 1월 28일, 1월 29일, 1월 30일
        if (year == 2025 && month == 1 && day >= 28 && day <= 30) return true;
        
        // 2026년 설날: 2월 17일 (음력 1월 1일), 연휴: 2월 16일, 2월 17일, 2월 18일
        if (year == 2026 && month == 2 && day >= 16 && day <= 18) return true;
        
        // 추석 연휴 (음력 8월 15일 전후, 양력으로 근사치 계산)
        // 2022년 추석: 9월 10일 (음력 8월 15일), 연휴: 9월 9일, 9월 10일, 9월 11일, 9월 12일
        if (year == 2022 && month == 9 && day >= 9 && day <= 12) return true;
        
        // 2023년 추석: 9월 29일 (음력 8월 15일), 연휴: 9월 28일, 9월 29일, 9월 30일
        if (year == 2023 && month == 9 && day >= 28 && day <= 30) return true;
        
        // 2024년 추석: 9월 17일 (음력 8월 15일), 연휴: 9월 16일, 9월 17일, 9월 18일
        if (year == 2024 && month == 9 && day >= 16 && day <= 18) return true;
        
        // 2025년 추석: 10월 6일 (음력 8월 15일), 연휴: 10월 5일, 10월 6일, 10월 7일, 10월 8일
        if (year == 2025 && month == 10 && day >= 5 && day <= 8) return true;
        
        // 연말 폐장일 (12월 마지막 평일)
        // 규칙: 12월의 마지막 평일은 휴장일, 그 전날 평일이 폐장일(마지막 거래일)
        // 예: 2025년 12월 31일이 수요일(평일) → 12월 31일은 휴장, 12월 30일이 폐장일(마지막 거래일)
        // 예: 2023년 12월 30일(토), 31일(일)이 주말 → 12월 29일(금)이 마지막 평일(휴장), 12월 28일(목)이 폐장일(마지막 거래일)
        if (month == 12) {
            LocalDate lastWeekday = getLastWeekdayOfYear(year);
            // 마지막 평일은 휴장일
            if (date.equals(lastWeekday)) {
                return true; // 마지막 평일은 휴장
            }
        }
        
        return false;
    }
    
    /**
     * 해당 연도의 마지막 평일 계산 (12월 31일부터 역순으로 평일 찾기)
     * 마지막 평일은 휴장일, 그 전날 평일이 폐장일(마지막 거래일)
     */
    private LocalDate getLastWeekdayOfYear(int year) {
        LocalDate date = LocalDate.of(year, 12, 31);
        
        // 12월 31일부터 역순으로 평일 찾기 (최대 5일)
        for (int i = 0; i < 5; i++) {
            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                dayOfWeek == java.time.DayOfWeek.SUNDAY);
            
            // 주말이 아니면 평일 (공휴일 체크는 하지 않음, 마지막 평일 자체가 휴장일이므로)
            if (!isWeekend) {
                return date;
            }
            
            date = date.minusDays(1);
        }
        
        // 못 찾으면 12월 31일 반환
        return LocalDate.of(year, 12, 31);
    }
    
    /**
     * 해당 연도의 폐장일(마지막 거래일) 계산
     * 마지막 평일의 전날 평일이 폐장일
     */
    private LocalDate getMarketCloseDayOfYear(int year) {
        LocalDate lastWeekday = getLastWeekdayOfYear(year);
        
        // 마지막 평일의 전날부터 역순으로 평일 찾기 (최대 5일)
        LocalDate date = lastWeekday.minusDays(1);
        for (int i = 0; i < 5; i++) {
            java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                dayOfWeek == java.time.DayOfWeek.SUNDAY);
            
            // 공휴일 체크 (폐장일 제외, 재귀 방지)
            boolean isHoliday = isKoreanHolidayWithoutMarketClose(date);
            
            // 주말도 아니고 공휴일도 아니면 폐장일(마지막 거래일)
            if (!isWeekend && !isHoliday) {
                return date;
            }
            
            date = date.minusDays(1);
        }
        
        // 못 찾으면 마지막 평일의 전날 반환
        return lastWeekday.minusDays(1);
    }
    
    
    /**
     * 한국 공휴일인지 확인 (폐장일 제외, 재귀 방지용)
     */
    private boolean isKoreanHolidayWithoutMarketClose(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        
        // 고정 공휴일
        if (month == 1 && day == 1) return true; // 신정
        if (month == 3 && day == 1) return true; // 삼일절
        if (month == 5 && day == 5) return true; // 어린이날
        if (month == 6 && day == 6) return true; // 현충일
        if (month == 8 && day == 15) return true; // 광복절
        if (month == 10 && day == 3) return true; // 개천절
        if (month == 10 && day == 9) return true; // 한글날
        if (month == 12 && day == 25) return true; // 크리스마스
        
        // 특정 연도의 선거일
        if (year == 2022 && month == 3 && day == 9) return true; // 제20대 대통령선거
        if (year == 2024 && month == 4 && day == 10) return true; // 제22대 국회의원선거
        
        // 설날 연휴
        if (year == 2022 && month == 1 && day == 31) return true;
        if (year == 2022 && month == 2 && day >= 1 && day <= 2) return true;
        if (year == 2023 && month == 1 && day >= 21 && day <= 23) return true;
        if (year == 2024 && month == 2 && day >= 9 && day <= 11) return true;
        if (year == 2025 && month == 1 && day >= 28 && day <= 30) return true;
        if (year == 2026 && month == 2 && day >= 16 && day <= 18) return true;
        
        // 추석 연휴
        if (year == 2022 && month == 9 && day >= 9 && day <= 12) return true;
        if (year == 2023 && month == 9 && day >= 28 && day <= 30) return true;
        if (year == 2024 && month == 9 && day >= 16 && day <= 18) return true;
        if (year == 2025 && month == 10 && day >= 5 && day <= 8) return true;
        
        // 폐장일은 체크하지 않음 (재귀 방지)
        
        return false;
    }
    
    /**
     * 이전 영업일 계산 (주말 및 공휴일 건너뛰기)
     * 월요일의 전일 = 금요일
     * 공휴일 다음날의 전일 = 공휴일 전 마지막 거래일
     */
    /**
     * 종목별 이전 거래일 계산
     * 한국 주식: 한국 공휴일과 주말 건너뛰기
     * 미국 주식: 주말만 건너뛰기
     */
    private LocalDate getPreviousTradingDateForStock(String stockCode, LocalDate date) {
        LocalDate previousDate = date.minusDays(1);
        
        // 최대 10일 전까지 확인 (공휴일 연휴 대비)
        int maxDays = 10;
        int checkedDays = 0;
        
        boolean isKorean = isKoreanStock(stockCode);
        
        while (checkedDays < maxDays) {
            java.time.DayOfWeek dayOfWeek = previousDate.getDayOfWeek();
            boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                dayOfWeek == java.time.DayOfWeek.SUNDAY);
            
            // 주말은 모든 주식에서 건너뛰기
            if (isWeekend) {
                previousDate = previousDate.minusDays(1);
                checkedDays++;
                continue;
            }
            
            // 한국 주식인 경우에만 공휴일 체크
            if (isKorean) {
                boolean isHoliday = isKoreanHoliday(previousDate);
                if (isHoliday) {
                    previousDate = previousDate.minusDays(1);
                    checkedDays++;
                    continue;
                }
            }
            
            // 주말도 아니고 (한국 주식인 경우) 공휴일도 아니면 거래일
            return previousDate;
        }
        
        // 10일 전까지도 못 찾으면 그냥 반환 (데이터 부족)
        return previousDate;
    }
    
    /**
     * 한국 주식용 이전 거래일 계산 (레거시 지원)
     */
    private LocalDate getPreviousTradingDate(LocalDate date) {
        LocalDate previousDate = date.minusDays(1);
        
        // 최대 10일 전까지 확인 (공휴일 연휴 대비)
        int maxDays = 10;
        int checkedDays = 0;
        
        while (checkedDays < maxDays) {
            java.time.DayOfWeek dayOfWeek = previousDate.getDayOfWeek();
            boolean isWeekend = (dayOfWeek == java.time.DayOfWeek.SATURDAY || 
                                dayOfWeek == java.time.DayOfWeek.SUNDAY);
            boolean isHoliday = isKoreanHoliday(previousDate);
            
            // 주말도 아니고 공휴일도 아니면 거래일
            if (!isWeekend && !isHoliday) {
                return previousDate;
            }
            
            // 주말이거나 공휴일이면 하루 더 전으로
            previousDate = previousDate.minusDays(1);
            checkedDays++;
        }
        
        // 10일 전까지도 못 찾으면 그냥 반환 (데이터 부족)
        return previousDate;
    }
    
    /**
     * 환율 조회
     */
    private BigDecimal getExchangeRate(String fromCurrency, String toCurrency, LocalDate date) {
        if (fromCurrency == null || toCurrency == null || fromCurrency.equals(toCurrency)) {
            return BigDecimal.ONE;
        }
        
        // 날짜가 null이면 오늘 날짜 사용
        if (date == null) {
            date = LocalDate.now();
        }
        
        try {
            // 직접 환율 조회 시도 (예: USD -> KRW → "USDKRW")
            String currencyPair = fromCurrency + toCurrency;
            Optional<ExchangeRate> exchangeRate = exchangeRateRepository
                .findByCurrencyPairAndRateDate(currencyPair, date);
            
            if (exchangeRate.isPresent()) {
                return exchangeRate.get().getRate();
            }
            
            // 해당 날짜에 없으면 최신 환율 조회 시도
            exchangeRate = exchangeRateRepository.findLatestByCurrencyPair(currencyPair);
            if (exchangeRate.isPresent()) {
                return exchangeRate.get().getRate();
            }
            
            // 역환율 조회 시도 (예: KRW -> USD는 USD -> KRW의 역수)
            String reversePair = toCurrency + fromCurrency;
            Optional<ExchangeRate> reverseRate = exchangeRateRepository
                .findByCurrencyPairAndRateDate(reversePair, date);
            
            if (reverseRate.isPresent()) {
                return BigDecimal.ONE.divide(reverseRate.get().getRate(), 8, RoundingMode.HALF_UP);
            }
            
            // 최신 역환율 조회 시도
            reverseRate = exchangeRateRepository.findLatestByCurrencyPair(reversePair);
            if (reverseRate.isPresent()) {
                return BigDecimal.ONE.divide(reverseRate.get().getRate(), 8, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.warn("환율 조회 실패: {} -> {}, date={}, error={}", fromCurrency, toCurrency, date, e.getMessage());
        }
        
        // 기본값: 1.0 (같은 통화로 가정)
        log.warn("환율을 찾을 수 없습니다: {} -> {} (date={}), 기본값 1.0 사용", fromCurrency, toCurrency, date);
        return BigDecimal.ONE;
    }
    
    /**
     * 보유 정보를 담는 내부 클래스
     */
    private static class HoldingInfo {
        private BigDecimal totalBuyQuantity = BigDecimal.ZERO;
        private BigDecimal totalBuyAmount = BigDecimal.ZERO;
        private BigDecimal totalSellQuantity = BigDecimal.ZERO;
        
        public void addBuyRecord(InvestmentRecord record) {
            if (record.getQuantity() != null) {
                totalBuyQuantity = totalBuyQuantity.add(record.getQuantity());
            }
            if (record.getTotalAmount() != null) {
                totalBuyAmount = totalBuyAmount.add(record.getTotalAmount());
            }
        }
        
        public void subtractSellQuantity(BigDecimal quantity) {
            if (quantity != null) {
                totalSellQuantity = totalSellQuantity.add(quantity);
            }
        }
        
        public BigDecimal getHoldingQuantity() {
            return totalBuyQuantity.subtract(totalSellQuantity);
        }
        
        public BigDecimal getAveragePrice() {
            if (totalBuyQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return totalBuyAmount.divide(totalBuyQuantity, 4, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * 캘린더 데이터 사전 로딩 캐시 (성능 최적화용)
     * DB 쿼리를 최소화하기 위해 모든 데이터를 한 번에 로드하여 메모리에서 처리
     */
    private static class CalendarCacheData {
        final List<InvestmentRecord> allUserRecords;
        final Map<String, Map<LocalDate, StockPrice>> priceCache;
        final Map<String, BigDecimal> exchangeRateCache;
        final String userCurrency;
        final Map<String, Boolean> koreanStockCache;
        
        CalendarCacheData(List<InvestmentRecord> allUserRecords,
                         Map<String, Map<LocalDate, StockPrice>> priceCache,
                         Map<String, BigDecimal> exchangeRateCache,
                         String userCurrency,
                         Map<String, Boolean> koreanStockCache) {
            this.allUserRecords = allUserRecords;
            this.priceCache = priceCache;
            this.exchangeRateCache = exchangeRateCache;
            this.userCurrency = userCurrency;
            this.koreanStockCache = koreanStockCache;
        }
        
        /** 종가 조회 (캐시에서) */
        Optional<StockPrice> getStockPrice(String stockCode, LocalDate date) {
            Map<LocalDate, StockPrice> dateMap = priceCache.get(stockCode);
            if (dateMap == null) return Optional.empty();
            return Optional.ofNullable(dateMap.get(date));
        }
        
        /** 환율 조회 (캐시에서) */
        BigDecimal getExchangeRate(String fromCurrency, String toCurrency) {
            if (fromCurrency == null || toCurrency == null || fromCurrency.equals(toCurrency)) {
                return BigDecimal.ONE;
            }
            String pair = fromCurrency + toCurrency;
            return exchangeRateCache.getOrDefault(pair, BigDecimal.ONE);
        }
        
        /** 한국 주식 여부 (캐시에서) */
        boolean isKoreanStock(String stockCode) {
            return koreanStockCache.getOrDefault(stockCode, 
                stockCode != null && stockCode.matches("^[0-9]+$"));
        }
        
        /** 특정 날짜 기준 보유 종목 계산 (메모리 필터링) */
        Map<String, HoldingInfo> getHoldingsAsOf(LocalDate buyRecordsUpTo, LocalDate sellRecordsUpTo) {
            Map<String, HoldingInfo> holdings = new HashMap<>();
            
            for (InvestmentRecord record : allUserRecords) {
                if (record.getAssetType() == AssetType.CURRENCY) continue;
                
                if (record.getType() == InvestmentRecord.InvestmentType.BUY 
                    && record.getStockCode() != null && record.getQuantity() != null
                    && !record.getRecordDate().isAfter(buyRecordsUpTo)) {
                    String stockCode = record.getStockCode();
                    HoldingInfo holding = holdings.getOrDefault(stockCode, new HoldingInfo());
                    holding.addBuyRecord(record);
                    holdings.put(stockCode, holding);
                }
            }
            
            for (InvestmentRecord record : allUserRecords) {
                if (record.getAssetType() == AssetType.CURRENCY) continue;
                
                if (record.getType() == InvestmentRecord.InvestmentType.SELL
                    && record.getStockCode() != null && record.getSellQuantity() != null
                    && !record.getRecordDate().isAfter(sellRecordsUpTo)) {
                    String stockCode = record.getStockCode();
                    if (holdings.containsKey(stockCode)) {
                        holdings.get(stockCode).subtractSellQuantity(record.getSellQuantity());
                    }
                }
            }
            
            holdings.entrySet().removeIf(entry -> 
                entry.getValue().getHoldingQuantity().compareTo(BigDecimal.ZERO) <= 0);
            
            return holdings;
        }
    }
    
    /**
     * 주간 투자 요약 조회
     */
    public ApiResponse<Map<String, Object>> getWeeklySummary(Long userId, LocalDate weekStart) {
        log.info("사용자 {}의 주간 투자 요약 조회: {}", userId, weekStart);
        
        LocalDate weekEnd = weekStart.plusDays(6);
        
        List<InvestmentRecord> records = investmentRecordRepository
            .findRecordsByDateRange(userId, weekStart, weekEnd);
        
        Map<String, Object> weeklySummary = calculateWeeklySummary(records, weekStart, weekEnd);
        
        return ApiResponse.success(weeklySummary, "주간 투자 요약을 조회했습니다.");
    }
    
    /**
     * 주간 요약 계산
     */
    private Map<String, Object> calculateWeeklySummary(List<InvestmentRecord> records, 
                                                      LocalDate weekStart, LocalDate weekEnd) {
        // 투자 일수
        long investmentDays = records.stream()
            .map(InvestmentRecord::getRecordDate)
            .distinct()
            .count();
        
        // 총 투자 금액
        BigDecimal totalInvestment = records.stream()
            .filter(record -> record.getTotalAmount() != null)
            .map(InvestmentRecord::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 총 수익
        BigDecimal totalProfit = records.stream()
            .filter(record -> record.getUnrealizedProfitAmount() != null)
            .map(InvestmentRecord::getUnrealizedProfitAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 평균 수익률
        double avgProfitRate = 0.0;
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            avgProfitRate = totalProfit
                .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        }
        
        // 투자 유형별 통계
        long buyCount = records.stream()
            .filter(record -> record.getType() == InvestmentRecord.InvestmentType.BUY)
            .count();
        
        long sellCount = records.stream()
            .filter(record -> record.getType() == InvestmentRecord.InvestmentType.SELL)
            .count();
        
        return Map.of(
            "weekStart", weekStart.toString(),
            "weekEnd", weekEnd.toString(),
            "investmentDays", investmentDays,
            "totalInvestment", totalInvestment,
            "totalProfit", totalProfit,
            "avgProfitRate", Math.round(avgProfitRate * 100.0) / 100.0,
            "buyCount", buyCount,
            "sellCount", sellCount,
            "totalRecords", records.size()
        );
    }
    
    /**
     * 월간 투자 요약 조회
     */
    public ApiResponse<Map<String, Object>> getMonthlySummary(Long userId, int year, int month) {
        log.info("사용자 {}의 {}-{} 월간 투자 요약 조회", userId, year, month);
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        List<InvestmentRecord> records = investmentRecordRepository
            .findRecordsByDateRange(userId, startDate, endDate);
        
        Map<String, Object> monthlySummary = calculateMonthlySummary(records, year, month);
        
        return ApiResponse.success(monthlySummary, "월간 투자 요약을 조회했습니다.");
    }
    
    /**
     * 월간 요약 계산
     */
    private Map<String, Object> calculateMonthlySummary(List<InvestmentRecord> records, int year, int month) {
        // 투자 일수
        long investmentDays = records.stream()
            .map(InvestmentRecord::getRecordDate)
            .distinct()
            .count();
        
        // 총 투자 금액
        BigDecimal totalInvestment = records.stream()
            .filter(record -> record.getTotalAmount() != null)
            .map(InvestmentRecord::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 총 수익
        BigDecimal totalProfit = records.stream()
            .filter(record -> record.getUnrealizedProfitAmount() != null)
            .map(InvestmentRecord::getUnrealizedProfitAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 평균 수익률
        double avgProfitRate = 0.0;
        if (totalInvestment.compareTo(BigDecimal.ZERO) > 0) {
            avgProfitRate = totalProfit
                .divide(totalInvestment, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
        }
        
        // 투자 유형별 통계
        long buyCount = records.stream()
            .filter(record -> record.getType() == InvestmentRecord.InvestmentType.BUY)
            .count();
        
        long sellCount = records.stream()
            .filter(record -> record.getType() == InvestmentRecord.InvestmentType.SELL)
            .count();
        
        // 종목별 투자 금액 (상위 5개)
        Map<String, BigDecimal> stockInvestment = records.stream()
            .filter(record -> record.getStockName() != null && record.getTotalAmount() != null)
            .collect(Collectors.groupingBy(
                InvestmentRecord::getStockName,
                Collectors.reducing(BigDecimal.ZERO, InvestmentRecord::getTotalAmount, BigDecimal::add)
            ));
        
        List<Map<String, Object>> topStocks = stockInvestment.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .limit(5)
            .map(entry -> {
                Map<String, Object> stockData = new java.util.HashMap<>();
                stockData.put("stockName", entry.getKey());
                stockData.put("investmentAmount", entry.getValue());
                return stockData;
            })
            .collect(Collectors.toList());
        
        return Map.of(
            "year", year,
            "month", month,
            "investmentDays", investmentDays,
            "totalInvestment", totalInvestment,
            "totalProfit", totalProfit,
            "avgProfitRate", Math.round(avgProfitRate * 100.0) / 100.0,
            "buyCount", buyCount,
            "sellCount", sellCount,
            "totalRecords", records.size(),
            "topStocks", topStocks
        );
    }
    
    /**
     * 투자 패턴 분석
     */
    public ApiResponse<Map<String, Object>> getInvestmentPattern(Long userId, int year, int month) {
        log.info("사용자 {}의 {}-{} 투자 패턴 분석", userId, year, month);
        
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        
        List<InvestmentRecord> records = investmentRecordRepository
            .findRecordsByDateRange(userId, startDate, endDate);
        
        Map<String, Object> pattern = analyzeInvestmentPattern(records);
        
        return ApiResponse.success(pattern, "투자 패턴 분석이 완료되었습니다.");
    }
    
    /**
     * 투자 패턴 분석
     */
    private Map<String, Object> analyzeInvestmentPattern(List<InvestmentRecord> records) {
        if (records.isEmpty()) {
            return Map.of(
                "message", "해당 기간에 투자 기록이 없습니다.",
                "pattern", "NONE"
            );
        }
        
        // 요일별 투자 빈도
        Map<String, Long> dayOfWeekPattern = records.stream()
            .collect(Collectors.groupingBy(
                record -> record.getRecordDate().getDayOfWeek().toString(),
                Collectors.counting()
            ));
        
        // 시간대별 투자 빈도 (생성 시간 기준)
        Map<String, Long> hourPattern = records.stream()
            .collect(Collectors.groupingBy(
                record -> String.valueOf(record.getCreatedAt().getHour()),
                Collectors.counting()
            ));
        
        // 투자 유형별 비율
        long buyCount = records.stream()
            .filter(record -> record.getType() == InvestmentRecord.InvestmentType.BUY)
            .count();
        
        long sellCount = records.stream()
            .filter(record -> record.getType() == InvestmentRecord.InvestmentType.SELL)
            .count();
        
        double buyRatio = records.isEmpty() ? 0.0 : (double) buyCount / records.size() * 100;
        double sellRatio = records.isEmpty() ? 0.0 : (double) sellCount / records.size() * 100;
        
        // 패턴 유형 판단
        String patternType = determinePatternType(buyRatio, sellRatio, dayOfWeekPattern);
        
        return Map.of(
            "dayOfWeekPattern", dayOfWeekPattern,
            "hourPattern", hourPattern,
            "buyRatio", Math.round(buyRatio * 100.0) / 100.0,
            "sellRatio", Math.round(sellRatio * 100.0) / 100.0,
            "patternType", patternType,
            "totalRecords", records.size()
        );
    }
    
    /**
     * 패턴 유형 판단
     */
    private String determinePatternType(double buyRatio, double sellRatio, Map<String, Long> dayOfWeekPattern) {
        if (buyRatio > 80) {
            return "AGGRESSIVE_BUYER";
        } else if (sellRatio > 80) {
            return "ACTIVE_SELLER";
        } else if (buyRatio > 60) {
            return "MODERATE_BUYER";
        } else if (sellRatio > 60) {
            return "MODERATE_SELLER";
        } else if (dayOfWeekPattern.size() <= 2) {
            return "CONCENTRATED_TRADER";
        } else {
            return "BALANCED_TRADER";
        }
    }
} 