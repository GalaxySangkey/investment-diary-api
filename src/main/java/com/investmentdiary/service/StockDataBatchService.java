package com.investmentdiary.service;

import com.investmentdiary.entity.*;
import com.investmentdiary.repository.*;
import com.investmentdiary.service.provider.MarketDataProviderRouter;
import com.investmentdiary.util.TickerConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 주식 종가, 배당금, 환율 데이터를 매일/매월 조회하여 DB에 저장하는 배치 작업 서비스
 */
@Slf4j
@Service
public class StockDataBatchService {
    
    private final MarketDataProviderRouter marketDataProviderRouter;
    private final InvestmentRecordRepository investmentRecordRepository;
    private final StockPriceRepository stockPriceRepository;
    private final StockDividendRepository stockDividendRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final StockTickerMappingRepository tickerMappingRepository;
    private final TickerConverter tickerConverter;

    @Value("${market-data.yahoo.batch-delay-ms:550}")
    private int yahooBatchDelayMs;
    
    @Autowired
    public StockDataBatchService(
            MarketDataProviderRouter marketDataProviderRouter,
            InvestmentRecordRepository investmentRecordRepository,
            StockPriceRepository stockPriceRepository,
            StockDividendRepository stockDividendRepository,
            ExchangeRateRepository exchangeRateRepository,
            StockTickerMappingRepository tickerMappingRepository,
            TickerConverter tickerConverter) {
        this.marketDataProviderRouter = marketDataProviderRouter;
        this.investmentRecordRepository = investmentRecordRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.stockDividendRepository = stockDividendRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.tickerMappingRepository = tickerMappingRepository;
        this.tickerConverter = tickerConverter;
    }
    
    /**
     * 매일 오전 9시에 실행 (한국 시간 기준, 장 시작 전)
     * 회원들이 보유 중인 종목들의 종가를 조회하여 DB에 저장
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void updateDailyStockPrices() {
        log.info("일일 주식 종가 업데이트 시작");
        
        try {
            LocalDate today = LocalDate.now();
            
            // 1. 모든 회원의 매수 기록 조회
            List<InvestmentRecord> buyRecords = investmentRecordRepository.findAll()
                .stream()
                .filter(record -> record.getType() == InvestmentRecord.InvestmentType.BUY
                    && record.getAssetType() == AssetType.STOCK
                    && record.getStockCode() != null && !record.getStockCode().isEmpty()
                    && !record.getIsDeleted())
                .collect(Collectors.toList());
            
            log.info("매수 기록 수: {}", buyRecords.size());
            
            // 2. 매도 기록 조회
            List<InvestmentRecord> sellRecords = investmentRecordRepository.findAll()
                .stream()
                .filter(record -> record.getType() == InvestmentRecord.InvestmentType.SELL
                    && record.getAssetType() == AssetType.STOCK
                    && record.getStockCode() != null && !record.getStockCode().isEmpty()
                    && !record.getIsDeleted())
                .collect(Collectors.toList());
            
            log.info("매도 기록 수: {}", sellRecords.size());
            
            // 3. 종목코드별로 매수 수량과 매도 수량 집계
            // 청산 후 다시 매수한 경우도 올바르게 처리하기 위해 종목코드 기준으로 계산
            Map<String, BigDecimal> buyQuantityByStockCode = new HashMap<>();
            Map<String, BigDecimal> sellQuantityByStockCode = new HashMap<>();
            
            // 매수 수량 집계 (종목코드별)
            for (InvestmentRecord buyRecord : buyRecords) {
                String stockCode = buyRecord.getStockCode();
                BigDecimal quantity = buyRecord.getQuantity() != null ? buyRecord.getQuantity() : BigDecimal.ZERO;
                buyQuantityByStockCode.put(stockCode, 
                    buyQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO).add(quantity));
            }
            
            // 매도 수량 집계 (종목코드별)
            // 매도 기록의 stockCode를 사용 (selectedStockId가 아닌)
            for (InvestmentRecord sellRecord : sellRecords) {
                String stockCode = sellRecord.getStockCode();
                BigDecimal sellQuantity = sellRecord.getSellQuantity() != null ? sellRecord.getSellQuantity() : BigDecimal.ZERO;
                sellQuantityByStockCode.put(stockCode, 
                    sellQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO).add(sellQuantity));
            }
            
            // 4. 실제 보유 중인 종목 필터링 (종목코드별 매수 수량 > 매도 수량)
            Set<String> heldStockCodes = new HashSet<>();
            for (String stockCode : buyQuantityByStockCode.keySet()) {
                BigDecimal buyQuantity = buyQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO);
                BigDecimal sellQuantity = sellQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO);
                
                if (buyQuantity.compareTo(sellQuantity) > 0) {
                    heldStockCodes.add(stockCode);
                    log.debug("보유 종목: {} (매수: {}, 매도: {}, 보유: {})", 
                        stockCode, buyQuantity, sellQuantity, buyQuantity.subtract(sellQuantity));
                }
            }
            
            log.info("보유 중인 종목 수: {}", heldStockCodes.size());
            log.info("보유 종목 목록: {}", heldStockCodes);
            
            // 5. 최근 7일간 누락/불일치 종가 데이터 채우기 (과거 데이터 보정)
            LocalDate recentStartDate = today.minusDays(7);
            log.info("최근 누락 종가 데이터 보정 시작: {} ~ {}", recentStartDate, today);
            
            int fillSuccessCount = 0;
            int fillFailCount = 0;
            List<String> fillFailedStocks = new ArrayList<>();
            
            for (String stockCode : heldStockCodes) {
                try {
                    log.info("최근 종가 보정 시작: {}, 기간: {} ~ {}", stockCode, recentStartDate, today);
                    int filled = fillHistoricalPrices(stockCode, recentStartDate, today);
                    fillSuccessCount++;
                    log.info("최근 종가 보정 완료: {}, 처리건수: {}", stockCode, filled);
                    
                    sleepYahooBatchDelay();
                    
                } catch (Exception e) {
                    log.error("최근 종가 보정 실패: {} - {}", stockCode, e.getMessage(), e);
                    fillFailCount++;
                    fillFailedStocks.add(stockCode);
                }
            }
            
            log.info("최근 누락 종가 데이터 보정 완료: 성공 {}, 실패 {}", fillSuccessCount, fillFailCount);
            if (!fillFailedStocks.isEmpty()) {
                log.warn("보정 실패한 종목 목록: {}", fillFailedStocks);
            }
            
            // 6. 각 종목의 최신 종가 조회 및 저장
            int successCount = 0;
            int failCount = 0;
            List<String> failedStocks = new ArrayList<>();
            
            for (String stockCode : heldStockCodes) {
                try {
                    log.info("최신 종가 조회 시작: {}", stockCode);
                    // 각 종목별로 별도 트랜잭션으로 처리 (하나 실패해도 다른 것들은 저장되도록)
                    saveStockPriceForTicker(stockCode, today);
                    successCount++;
                    log.info("최신 종가 조회 성공: {}", stockCode);
                    
                    sleepYahooBatchDelay();
                    
                } catch (Exception e) {
                    log.error("최신 종가 조회 실패: {} - {}", stockCode, e.getMessage(), e);
                    failCount++;
                    failedStocks.add(stockCode);
                    // 개별 종목 실패는 전체 배치를 중단하지 않음
                }
            }
            
            log.info("일일 주식 종가 업데이트 완료: 최신종가 성공 {}, 실패 {}", successCount, failCount);
            if (!failedStocks.isEmpty()) {
                log.warn("실패한 종목 목록: {}", failedStocks);
            }
            
        } catch (Exception e) {
            log.error("일일 주식 종가 업데이트 중 오류 발생", e);
            // 예외를 다시 던지지 않음 (스케줄러에서 예외가 발생해도 다음 실행에 영향 없도록)
        }
    }
    
    /**
     * 매월 1일 오전 10시에 실행
     * 회원들이 보유 중인 종목들의 배당 정보를 조회하여 DB에 저장
     */
    @Scheduled(cron = "0 0 10 1 * *", zone = "Asia/Seoul")
    public void updateMonthlyDividends() {
        log.info("월별 배당 정보 업데이트 시작");
        
        try {
            LocalDate today = LocalDate.now();
            
            // 1. 모든 회원의 매수 기록 조회
            List<InvestmentRecord> buyRecords = investmentRecordRepository.findAll()
                .stream()
                .filter(record -> record.getType() == InvestmentRecord.InvestmentType.BUY
                    && record.getAssetType() == AssetType.STOCK
                    && record.getStockCode() != null && !record.getStockCode().isEmpty()
                    && !record.getIsDeleted())
                .collect(Collectors.toList());
            
            log.info("매수 기록 수: {}", buyRecords.size());
            
            // 2. 매도 기록 조회
            List<InvestmentRecord> sellRecords = investmentRecordRepository.findAll()
                .stream()
                .filter(record -> record.getType() == InvestmentRecord.InvestmentType.SELL
                    && record.getAssetType() == AssetType.STOCK
                    && record.getStockCode() != null && !record.getStockCode().isEmpty()
                    && !record.getIsDeleted())
                .collect(Collectors.toList());
            
            log.info("매도 기록 수: {}", sellRecords.size());
            
            // 3. 종목코드별로 매수 수량과 매도 수량 집계
            Map<String, BigDecimal> buyQuantityByStockCode = new HashMap<>();
            Map<String, BigDecimal> sellQuantityByStockCode = new HashMap<>();
            
            // 매수 수량 집계 (종목코드별)
            for (InvestmentRecord buyRecord : buyRecords) {
                String stockCode = buyRecord.getStockCode();
                BigDecimal quantity = buyRecord.getQuantity() != null ? buyRecord.getQuantity() : BigDecimal.ZERO;
                buyQuantityByStockCode.put(stockCode, 
                    buyQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO).add(quantity));
            }
            
            // 매도 수량 집계 (종목코드별)
            for (InvestmentRecord sellRecord : sellRecords) {
                String stockCode = sellRecord.getStockCode();
                BigDecimal sellQuantity = sellRecord.getSellQuantity() != null ? sellRecord.getSellQuantity() : BigDecimal.ZERO;
                sellQuantityByStockCode.put(stockCode, 
                    sellQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO).add(sellQuantity));
            }
            
            // 4. 실제 보유 중인 종목 필터링 (종목코드별 매수 수량 > 매도 수량)
            Set<String> heldStockCodes = new HashSet<>();
            for (String stockCode : buyQuantityByStockCode.keySet()) {
                BigDecimal buyQuantity = buyQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO);
                BigDecimal sellQuantity = sellQuantityByStockCode.getOrDefault(stockCode, BigDecimal.ZERO);
                
                if (buyQuantity.compareTo(sellQuantity) > 0) {
                    heldStockCodes.add(stockCode);
                    log.debug("보유 종목: {} (매수: {}, 매도: {}, 보유: {})", 
                        stockCode, buyQuantity, sellQuantity, buyQuantity.subtract(sellQuantity));
                }
            }
            
            log.info("보유 중인 종목 수: {}", heldStockCodes.size());
            log.info("보유 종목 목록: {}", heldStockCodes);
            
            // 5. 각 종목의 배당 정보 조회 및 저장
            int successCount = 0;
            int failCount = 0;
            List<String> failedStocks = new ArrayList<>();
            
            for (String stockCode : heldStockCodes) {
                try {
                    String ticker = getOrCreateTicker(stockCode);
                    if (ticker == null) {
                        failCount++;
                        continue;
                    }
                    
                    // 배당 정보 조회
                    Map<String, Object> dividendData = marketDataProviderRouter.getDividendInfo(ticker);
                    if (dividendData == null || dividendData.isEmpty()) {
                        log.debug("배당 정보가 없습니다: {} ({})", stockCode, ticker);
                        failCount++;
                        continue;
                    }
                    
                    // 배당 정보 처리
                    // 네이버 금융: annualDividend와 dividendPerShare가 모두 연간 배당금
                    // Alpha Vantage/yfinance: annualDividend는 연간, dividendPerShare는 분기별
                    BigDecimal annualDividend = (BigDecimal) dividendData.get("annualDividend");
                    BigDecimal dividendPerShareFromApi = (BigDecimal) dividendData.get("dividendPerShare");
                    
                    BigDecimal dividendPerShare;
                    if (annualDividend != null && dividendPerShareFromApi != null) {
                        // 둘 다 있는 경우: annualDividend는 연간, dividendPerShare는 분기별
                        // DB에는 연간 배당금을 저장 (dividendPerShare 필드에 연간 배당금 저장)
                        dividendPerShare = annualDividend;
                    } else if (annualDividend != null) {
                        // annualDividend만 있는 경우: 연간 배당금
                        dividendPerShare = annualDividend;
                    } else if (dividendPerShareFromApi != null) {
                        // dividendPerShare만 있는 경우
                        // 한국 주식(네이버 금융): 이미 연간 배당금
                        // 미국 주식(Alpha Vantage/yfinance): 분기별 배당금이므로 4를 곱해서 연간으로 변환
                        // 한국 주식인지 판단 (티커에 .KS 또는 .KQ가 있거나, stockCode가 숫자로만 구성)
                        boolean isKoreanStock = ticker.endsWith(".KS") || ticker.endsWith(".KQ") 
                            || (stockCode != null && stockCode.matches("^\\d+$"));
                        
                        if (isKoreanStock) {
                            // 한국 주식: 그대로 사용 (이미 연간)
                            dividendPerShare = dividendPerShareFromApi;
                            annualDividend = dividendPerShareFromApi;
                        } else {
                            // 미국 주식: 분기별이므로 4를 곱해서 연간으로 변환
                            dividendPerShare = dividendPerShareFromApi.multiply(BigDecimal.valueOf(4));
                            annualDividend = dividendPerShare;
                        }
                    } else {
                        log.debug("배당 정보가 불완전합니다: {} ({})", stockCode, ticker);
                        failCount++;
                        continue;
                    }
                    
                    if (dividendPerShare == null || annualDividend == null) {
                        failCount++;
                        continue;
                    }
                    
                    // 배당일 처리 (배당일이 없으면 null로 저장)
                    LocalDate dividendDate = null;
                    if (dividendData.get("dividendDate") != null) {
                        try {
                            String dividendDateStr = dividendData.get("dividendDate").toString();
                            dividendDate = LocalDate.parse(dividendDateStr);
                            log.debug("배당일 정보 사용: {} ({})", stockCode, dividendDate);
                        } catch (Exception e) {
                            log.warn("배당일 파싱 실패: {} ({})", stockCode, e.getMessage());
                            dividendDate = null; // 파싱 실패 시 null로 저장
                        }
                    } else {
                        log.debug("배당일 정보가 없습니다: {}", stockCode);
                    }
                    
                    // DB에 저장
                    // 배당일이 null인 경우 기존 레코드를 찾거나 새로 생성
                    StockDividend stockDividend = null;
                    if (dividendDate != null) {
                        // 배당일이 있으면 배당일로 조회
                        stockDividend = stockDividendRepository
                            .findByTickerAndDividendDate(ticker, dividendDate)
                            .orElse(null);
                    } else {
                        // 배당일이 없으면 티커로만 조회 (가장 최근 것)
                        List<StockDividend> dividends = stockDividendRepository.findByTickerOrderByDividendDateDesc(ticker);
                        // 배당일이 null인 것 우선, 없으면 가장 최근 것
                        stockDividend = dividends.stream()
                            .filter(d -> d.getDividendDate() == null)
                            .findFirst()
                            .orElse(dividends.isEmpty() ? null : dividends.get(0));
                    }
                    
                    // API에서 가져온 값 로그
                    log.info("API에서 가져온 배당 정보: {} ({}), 배당금={}, 배당일={}", 
                        stockCode, ticker, dividendPerShare, dividendDate);
                    
                    // 배당 기준 연도 결정 (7월 이후면 재작년 배당 사용)
                    int currentYear = today.getYear();
                    int currentMonth = today.getMonthValue();
                    int targetDividendYear = currentMonth >= 7 ? currentYear - 2 : currentYear - 2; // 재작년
                    
                    // 배당일이 있는 경우, 배당 기준 연도와 일치하는지 확인
                    boolean isTargetYearDividend = true;
                    if (dividendDate != null) {
                        int dividendYear = dividendDate.getYear();
                        isTargetYearDividend = (dividendYear == targetDividendYear);
                        log.info("배당 기준 연도 확인: stockCode={}, dividendDate={}, dividendYear={}, targetYear={}, isTargetYear={}", 
                            stockCode, dividendDate, dividendYear, targetDividendYear, isTargetYearDividend);
                    }
                    
                    // 항상 업데이트 (배당 기준 연도에 맞는 정보로 업데이트)
                    boolean shouldUpdate = false;
                    String skipReason = null;
                    
                    if (stockDividend == null) {
                        // 새로 생성
                        stockDividend = StockDividend.builder()
                            .ticker(ticker)
                            .stockCode(stockCode)
                            .dividendDate(dividendDate)
                            .build();
                        shouldUpdate = true;
                        log.info("새 배당 정보 생성: {} ({})", stockCode, ticker);
                    } else {
                        // DB에 저장된 기존 값 로그
                        log.info("DB에 저장된 기존 배당 정보: {} ({}), 배당금={}, 배당일={}", 
                            stockCode, ticker, stockDividend.getDividendPerShare(), stockDividend.getDividendDate());
                        
                        // 기존 배당일의 연도 확인
                        boolean existingIsTargetYear = true;
                        if (stockDividend.getDividendDate() != null) {
                            int existingDividendYear = stockDividend.getDividendDate().getYear();
                            existingIsTargetYear = (existingDividendYear == targetDividendYear);
                        }
                        
                        // 배당 기준 연도와 일치하지 않는 기존 정보는 항상 업데이트
                        if (!existingIsTargetYear) {
                            shouldUpdate = true;
                            log.info("배당 기준 연도 불일치로 업데이트 필요: stockCode={}, 기존연도={}, 목표연도={}", 
                                stockCode, stockDividend.getDividendDate() != null ? stockDividend.getDividendDate().getYear() : "null", targetDividendYear);
                        } else {
                            // 기존 레코드 업데이트 여부 확인
                            boolean dividendAmountChanged = false;
                            boolean dividendDateChanged = false;
                            boolean dividendDateAdded = false;
                            
                            // 배당금이 다르면 업데이트
                            if (stockDividend.getDividendPerShare() == null 
                                || stockDividend.getDividendPerShare().compareTo(dividendPerShare) != 0) {
                                shouldUpdate = true;
                                dividendAmountChanged = true;
                                log.info("배당금 변경 감지: {} -> {} (업데이트 필요)", 
                                    stockDividend.getDividendPerShare(), dividendPerShare);
                            } else {
                                log.debug("배당금 동일: {} (변경 없음)", dividendPerShare);
                            }
                            
                            // 배당일이 다르면 업데이트
                            if (dividendDate != null) {
                                if (stockDividend.getDividendDate() == null 
                                    || !stockDividend.getDividendDate().equals(dividendDate)) {
                                    shouldUpdate = true;
                                    dividendDateChanged = true;
                                    log.info("배당일 변경 감지: {} -> {} (업데이트 필요)", 
                                        stockDividend.getDividendDate(), dividendDate);
                                } else {
                                    log.debug("배당일 동일: {} (변경 없음)", dividendDate);
                                }
                            }
                            
                            // 배당일이 없었는데 새로 생겼으면 업데이트
                            if (stockDividend.getDividendDate() == null && dividendDate != null) {
                                shouldUpdate = true;
                                dividendDateAdded = true;
                                log.info("배당일 추가 감지: {} (업데이트 필요)", dividendDate);
                            }
                            
                            // 업데이트가 필요 없는 경우 이유 기록
                            if (!shouldUpdate) {
                                StringBuilder reason = new StringBuilder();
                                if (!dividendAmountChanged && !dividendDateChanged && !dividendDateAdded) {
                                    reason.append("배당금과 배당일 모두 동일");
                                }
                                skipReason = reason.toString();
                            }
                        }
                    }
                    
                    // 배당 기준 연도와 일치하는 정보만 업데이트 (단, 기존 정보가 배당 기준 연도와 다르면 무조건 업데이트)
                    if (shouldUpdate) {
                        // 기존 정보가 배당 기준 연도와 다르면 무조건 업데이트
                        boolean forceUpdate = false;
                        if (stockDividend != null && stockDividend.getDividendDate() != null) {
                            int existingYear = stockDividend.getDividendDate().getYear();
                            if (existingYear != targetDividendYear) {
                                forceUpdate = true;
                                log.info("기존 배당 정보가 배당 기준 연도와 다름: stockCode={}, 기존연도={}, 목표연도={}, 강제 업데이트", 
                                    stockCode, existingYear, targetDividendYear);
                            }
                        }
                        
                        // 배당 기준 연도와 일치하거나, 강제 업데이트가 필요한 경우
                        if (isTargetYearDividend || forceUpdate) {
                            if (stockDividend == null) {
                                stockDividend = StockDividend.builder()
                                    .ticker(ticker)
                                    .stockCode(stockCode)
                                    .dividendDate(dividendDate)
                                    .build();
                            }
                            
                            stockDividend.setStockName((String) buyRecords.stream()
                                .filter(r -> r.getStockCode().equals(stockCode))
                                .findFirst()
                                .map(InvestmentRecord::getStockName)
                                .orElse(""));
                            stockDividend.setDividendPerShare(dividendPerShare);
                            if (dividendData.get("dividendYield") != null) {
                                stockDividend.setDividendYield((BigDecimal) dividendData.get("dividendYield"));
                            }
                            if (annualDividend != null) {
                                stockDividend.setAnnualDividend(annualDividend);
                            }
                            if (dividendDate != null) {
                                stockDividend.setDividendDate(dividendDate);
                            }
                            
                            stockDividendRepository.save(stockDividend);
                            log.info("배당 정보 저장/업데이트 완료: {} ({}), 배당금={}, 배당일={}, 기준연도={}", 
                                stockCode, ticker, dividendPerShare, dividendDate, targetDividendYear);
                        } else {
                            log.warn("배당 정보 업데이트 건너뜀: {} ({}), 이유=배당 기준 연도 불일치 (API배당일={}, 목표연도={}), API배당금={}, DB배당금={}, DB배당일={}", 
                                stockCode, ticker, dividendDate, targetDividendYear,
                                dividendPerShare, stockDividend != null ? stockDividend.getDividendPerShare() : null, 
                                stockDividend != null ? stockDividend.getDividendDate() : null);
                        }
                    } else {
                        log.warn("배당 정보 업데이트 건너뜀: {} ({}), 이유={}, API배당금={}, DB배당금={}, API배당일={}, DB배당일={}", 
                            stockCode, ticker, skipReason != null ? skipReason : "알 수 없음",
                            dividendPerShare, stockDividend != null ? stockDividend.getDividendPerShare() : null, 
                            dividendDate, stockDividend != null ? stockDividend.getDividendDate() : null);
                    }
                    
                    // 해당 종목의 모든 매수 기록에 배당일 업데이트
                    List<InvestmentRecord> recordsToUpdate = buyRecords.stream()
                        .filter(r -> r.getStockCode().equals(stockCode))
                        .collect(Collectors.toList());
                    
                    for (InvestmentRecord record : recordsToUpdate) {
                        boolean recordShouldUpdate = false;
                        
                        // 배당일이 있고, 기존 배당일이 없거나 더 최신 배당일이면 업데이트
                        if (dividendDate != null) {
                            if (record.getDividendDate() == null || dividendDate.isAfter(record.getDividendDate())) {
                                record.setDividendDate(dividendDate);
                                recordShouldUpdate = true;
                            }
                        }
                        
                        // 배당금이 다르면 항상 업데이트 (배당일이 없어도)
                        if (record.getDividendPerShare() == null 
                            || record.getDividendPerShare().compareTo(dividendPerShare) != 0) {
                            record.setDividendPerShare(dividendPerShare);
                            recordShouldUpdate = true;
                        }
                        
                        if (recordShouldUpdate) {
                            record.calculateDividendRatio();
                            investmentRecordRepository.save(record);
                            log.info("투자 기록 배당 정보 업데이트: recordId={}, stockCode={}, dividendPerShare={}, dividendDate={}", 
                                record.getId(), stockCode, dividendPerShare, dividendDate);
                        }
                    }
                    
                    successCount++;
                    
                    sleepYahooBatchDelay();
                    
                } catch (Exception e) {
                    log.error("배당 정보 조회 실패: {} - {}", stockCode, e.getMessage(), e);
                    failCount++;
                    failedStocks.add(stockCode);
                }
            }
            
            log.info("월별 배당 정보 업데이트 완료: 성공 {}, 실패 {}", successCount, failCount);
            if (!failedStocks.isEmpty()) {
                log.warn("실패한 종목 목록: {}", failedStocks);
            }
            
        } catch (Exception e) {
            log.error("월별 배당 정보 업데이트 중 오류 발생", e);
        }
    }
    
    /**
     * 매일 오전 9시 30분에 실행
     * 주요 통화 환율 조회 및 저장
     */
    @Scheduled(cron = "0 30 9 * * *", zone = "Asia/Seoul")
    @Transactional
    public void updateDailyExchangeRates() {
        log.info("일일 환율 업데이트 시작");
        
        try {
            LocalDate today = LocalDate.now();
            
            // 주요 통화쌍
            List<String[]> currencyPairs = Arrays.asList(
                new String[]{"USD", "KRW"},
                new String[]{"EUR", "KRW"},
                new String[]{"JPY", "KRW"},
                new String[]{"CNY", "KRW"},
                new String[]{"GBP", "KRW"}
            );
            
            int successCount = 0;
            int failCount = 0;
            
            for (String[] pair : currencyPairs) {
                try {
                    String baseCurrency = pair[0];
                    String quoteCurrency = pair[1];
                    String currencyPair = baseCurrency + quoteCurrency;
                    
                    Map<String, Object> rateData = marketDataProviderRouter.getExchangeRate(
                        baseCurrency, quoteCurrency);
                    
                    if (rateData == null || rateData.get("rate") == null) {
                        log.warn("환율 데이터를 가져올 수 없습니다: {}", currencyPair);
                        failCount++;
                        continue;
                    }
                    
                    ExchangeRate exchangeRate = exchangeRateRepository
                        .findByCurrencyPairAndRateDate(currencyPair, today)
                        .orElse(ExchangeRate.builder()
                            .currencyPair(currencyPair)
                            .baseCurrency(baseCurrency)
                            .quoteCurrency(quoteCurrency)
                            .rateDate(today)
                            .build());
                    
                    exchangeRate.setRate((BigDecimal) rateData.get("rate"));
                    exchangeRateRepository.save(exchangeRate);
                    successCount++;
                    
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    log.error("환율 조회 실패: {}/{}", pair[0], pair[1], e);
                    failCount++;
                }
            }
            
            log.info("일일 환율 업데이트 완료: 성공 {}, 실패 {}", successCount, failCount);
            
        } catch (Exception e) {
            log.error("일일 환율 업데이트 중 오류 발생", e);
        }
    }
    
    /**
     * 개별 종목의 최신 종가를 저장 (별도 트랜잭션)
     * 하나의 종목이 실패해도 다른 종목들은 저장되도록 함
     * 매수 기록 생성 시 현재가 업데이트를 위해 public으로 노출
     * 
     * API가 반환한 priceDate를 그대로 사용하여 저장 (오늘 날짜가 아닐 수 있음 - 휴일/장마감 전 등)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void saveStockPriceForTicker(String stockCode, LocalDate today) {
        try {
            log.debug("saveStockPriceForTicker 시작: stockCode={}, today={}", stockCode, today);
            
            // 티커 매핑 조회 또는 생성
            String ticker = getOrCreateTicker(stockCode);
            if (ticker == null) {
                log.error("티커를 찾을 수 없습니다: stockCode={}", stockCode);
                throw new RuntimeException("티커를 찾을 수 없습니다: " + stockCode);
            }
            
            log.debug("티커 변환 완료: stockCode={} -> ticker={}", stockCode, ticker);
            
            // Yahoo Finance API로 종가 조회
            Map<String, Object> priceData = marketDataProviderRouter.getStockPrice(ticker);
            if (priceData == null || priceData.get("closePrice") == null) {
                log.error("종가 데이터를 가져올 수 없습니다: stockCode={}, ticker={}, priceData={}", 
                    stockCode, ticker, priceData);
                throw new RuntimeException("종가 데이터를 가져올 수 없습니다: " + stockCode + " (" + ticker + ")");
            }
            
            // API 응답의 priceDate 사용 (오늘 날짜가 아닐 수 있음 - 휴일, 장마감 전 등)
            LocalDate priceDateFromApi = (LocalDate) priceData.get("priceDate");
            if (priceDateFromApi == null) {
                log.warn("종가 데이터에 날짜 정보가 없습니다. 오늘 날짜 사용: stockCode={}, ticker={}", 
                    stockCode, ticker);
                priceDateFromApi = today;
            }
            
            if (!priceDateFromApi.equals(today)) {
                log.info("API가 반환한 날짜({})가 오늘({})과 다릅니다. API 날짜로 저장합니다: stockCode={}, ticker={}", 
                    priceDateFromApi, today, stockCode, ticker);
            }
            
            log.debug("종가 데이터 조회 완료: stockCode={}, ticker={}, priceDate={}, closePrice={}", 
                stockCode, ticker, priceDateFromApi, priceData.get("closePrice"));
            
            // DB에 저장 (이미 존재하면 업데이트) - API가 반환한 priceDate 사용
            StockPrice stockPrice = stockPriceRepository
                .findByTickerAndPriceDate(ticker, priceDateFromApi)
                .orElse(StockPrice.builder()
                    .ticker(ticker)
                    .stockCode(stockCode)
                    .priceDate(priceDateFromApi)
                    .build());
            
            stockPrice.setStockName((String) priceData.getOrDefault("stockName", ""));
            stockPrice.setClosePrice((BigDecimal) priceData.get("closePrice"));
            if (priceData.get("openPrice") != null) {
                stockPrice.setOpenPrice((BigDecimal) priceData.get("openPrice"));
            }
            if (priceData.get("highPrice") != null) {
                stockPrice.setHighPrice((BigDecimal) priceData.get("highPrice"));
            }
            if (priceData.get("lowPrice") != null) {
                stockPrice.setLowPrice((BigDecimal) priceData.get("lowPrice"));
            }
            if (priceData.get("volume") != null) {
                stockPrice.setVolume((Long) priceData.get("volume"));
            }
            if (priceData.get("currency") != null) {
                stockPrice.setCurrency((String) priceData.get("currency"));
            }
            
            stockPriceRepository.save(stockPrice);
            log.info("종가 저장 완료: stockCode={}, ticker={}, priceDate={}, closePrice={}", 
                stockCode, ticker, priceDateFromApi, priceData.get("closePrice"));
            
        } catch (Exception e) {
            log.error("saveStockPriceForTicker 실패: stockCode={}, error={}", stockCode, e.getMessage(), e);
            throw e; // 예외를 다시 던져서 상위에서 처리하도록
        }
    }
    
    /**
     * 종목코드로 티커를 가져오거나 생성
     */
    private String getOrCreateTicker(String stockCode) {
        // 먼저 매핑 테이블에서 조회
        Optional<StockTickerMapping> mapping = tickerMappingRepository.findByStockCode(stockCode);
        if (mapping.isPresent() && mapping.get().getIsActive()) {
            return mapping.get().getTicker();
        }
        
        // stockCode 형식으로 한국 주식인지 미국 주식인지 판단
        String ticker;
        String market;
        String country;
        
        // 숫자로만 구성되어 있으면 한국 주식, 그 외는 미국 주식으로 판단
        if (stockCode.matches("^[0-9]+$")) {
            // 한국 주식: 종목코드를 Yahoo Finance 티커로 변환
            ticker = tickerConverter.convertToYahooTicker(stockCode, "KOSPI");
            market = "KOSPI";
            country = "KR";
        } else {
            // 미국 주식: stockCode가 이미 Yahoo Finance 티커 형식 (예: AAPL, MSFT)
            ticker = stockCode;
            market = "NASDAQ"; // 기본값, 실제로는 NYSE일 수도 있지만 나중에 수정 가능
            country = "US";
        }
        
        // 매핑 저장
        InvestmentRecord sampleRecord = investmentRecordRepository.findAll()
            .stream()
            .filter(r -> r.getStockCode() != null && r.getStockCode().equals(stockCode))
            .findFirst()
            .orElse(null);
        
        if (sampleRecord != null) {
            StockTickerMapping newMapping = StockTickerMapping.builder()
                .stockCode(stockCode)
                .stockName(sampleRecord.getStockName())
                .ticker(ticker)
                .market(market)
                .country(country)
                .isActive(true)
                .build();
            tickerMappingRepository.save(newMapping);
        }
        
        return ticker;
    }
    
    /**
     * 특정 종목의 과거 종가 데이터 채우기
     * 매수 기록 생성 시 호출: 해당 매수일부터 오늘까지 종가 정보 조회
     * 종가 정보는 종목별 공유 데이터이므로, 매수일부터 오늘까지 전부 조회해두는 것이 효율적
     * (다른 사용자도 같은 종목을 보유할 수 있고, 전량 매도 후 재매수하는 경우도 대비)
     * 
     * @param stockCode 종목코드
     * @param buyDate 매수일 (이 날짜부터 오늘까지 조회)
     * @return 저장된 종가 데이터 개수
     */
    @Transactional
    public int fillHistoricalPricesForStock(String stockCode, LocalDate buyDate) {
        log.info("종목 {} 과거 종가 데이터 채우기 시작: {} ~ 오늘", stockCode, buyDate);
        
        LocalDate today = LocalDate.now();
        
        // 매수일부터 오늘까지 종가 정보 조회
        // 종가 정보는 종목별 공유 데이터이므로, 보유 기간과 관계없이 매수일부터 오늘까지 전부 조회
        int saved = fillHistoricalPrices(stockCode, buyDate, today);
        
        log.info("종목 {} 과거 종가 데이터 채우기 완료: {} 건 저장", stockCode, saved);
        return saved;
    }
    
    /**
     * 특정 종목의 과거 종가 데이터를 일괄 조회하여 DB에 저장
     * 매수 기록 생성 시 또는 수동으로 호출 가능
     * 
     * @param stockCode 종목코드
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜 (null이면 오늘)
     * @return 저장된 종가 데이터 개수
     */
    @Transactional
    public int fillHistoricalPrices(String stockCode, LocalDate startDate, LocalDate endDate) {
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        
        log.info("과거 종가 데이터 채우기 시작: stockCode={}, {} ~ {}", stockCode, startDate, endDate);
        
        String ticker = null;
        try {
            ticker = getOrCreateTicker(stockCode);
            if (ticker == null) {
                log.error("티커를 찾을 수 없습니다: stockCode={}", stockCode);
                return 0;
            }
            
            log.info("티커 변환 완료: stockCode={} -> ticker={}", stockCode, ticker);
            
            // Java에서 외부 시장 데이터를 직접 조회
            Map<String, Object> response = marketDataProviderRouter.getStockPriceHistory(ticker, startDate, endDate);
            
            if (response == null) {
                log.error("과거 종가 조회 실패: stockCode={}, ticker={}, response=null", stockCode, ticker);
                return 0;
            }
            
            if (!response.containsKey("prices")) {
                log.error("과거 종가 조회 실패: stockCode={}, ticker={}, response에 prices 키 없음, response={}", 
                    stockCode, ticker, response);
                return 0;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> prices = (List<Map<String, Object>>) response.get("prices");
            
            if (prices == null || prices.isEmpty()) {
                log.warn("과거 종가 데이터가 비어있음: stockCode={}, ticker={}, prices={}", stockCode, ticker, prices);
                return 0;
            }
            
            log.info("과거 종가 {} 건 조회됨: stockCode={}, ticker={}", prices.size(), stockCode, ticker);
            
            int savedCount = 0;
            int skippedCount = 0;
            
            // 종목명 조회
            String stockName = stockCode;
            Optional<StockTickerMapping> mapping = tickerMappingRepository.findByStockCode(stockCode);
            if (mapping.isPresent()) {
                stockName = mapping.get().getStockName();
            }
            
            // 통화 결정 (한국 주식인지 미국 주식인지 판단)
            String defaultCurrency = "KRW";
            boolean isKoreanStock = stockCode.matches("^[0-9]+$");
            if (!isKoreanStock) {
                // 미국 주식: 기본 통화를 USD로 설정
                defaultCurrency = "USD";
            }
            
            int updatedCount = 0;
            
            for (Map<String, Object> priceData : prices) {
                try {
                    String priceDateStr = (String) priceData.get("priceDate");
                    LocalDate priceDate = LocalDate.parse(priceDateStr);
                    
                    // 통화 정보 가져오기 (API 응답에 있으면 사용, 없으면 기본값)
                    String currency = (String) priceData.get("currency");
                    if (currency == null || currency.isEmpty()) {
                        currency = defaultCurrency;
                    }
                    
                    BigDecimal newClosePrice = toBigDecimal(priceData.get("closePrice"));
                    
                    // 이미 존재하는 데이터인지 확인
                    Optional<StockPrice> existing = stockPriceRepository
                        .findByStockCodeAndPriceDate(stockCode, priceDate);
                    if (!existing.isPresent()) {
                        existing = stockPriceRepository
                            .findByTickerAndPriceDate(ticker, priceDate);
                    }
                    
                    if (existing.isPresent()) {
                        // 기존 데이터가 있으면 종가가 다른지 확인하여 업데이트
                        StockPrice existingPrice = existing.get();
                        boolean needsUpdate = false;
                        
                        // 종가가 null이거나 다르면 업데이트
                        if (existingPrice.getClosePrice() == null || 
                            (newClosePrice != null && existingPrice.getClosePrice().compareTo(newClosePrice) != 0)) {
                            needsUpdate = true;
                            log.info("종가 불일치 감지 (업데이트): stockCode={}, date={}, DB종가={}, API종가={}", 
                                stockCode, priceDate, existingPrice.getClosePrice(), newClosePrice);
                        }
                        
                        if (needsUpdate) {
                            existingPrice.setClosePrice(newClosePrice);
                            existingPrice.setOpenPrice(toBigDecimal(priceData.get("openPrice")));
                            existingPrice.setHighPrice(toBigDecimal(priceData.get("highPrice")));
                            existingPrice.setLowPrice(toBigDecimal(priceData.get("lowPrice")));
                            existingPrice.setVolume(toLong(priceData.get("volume")));
                            existingPrice.setCurrency(currency);
                            existingPrice.setStockName(stockName);
                            stockPriceRepository.save(existingPrice);
                            updatedCount++;
                        } else {
                            skippedCount++;
                        }
                        continue;
                    }
                    
                    // 새로운 종가 데이터 저장
                    StockPrice stockPrice = StockPrice.builder()
                        .ticker(ticker)
                        .stockCode(stockCode)
                        .stockName(stockName)
                        .priceDate(priceDate)
                        .closePrice(newClosePrice)
                        .openPrice(toBigDecimal(priceData.get("openPrice")))
                        .highPrice(toBigDecimal(priceData.get("highPrice")))
                        .lowPrice(toBigDecimal(priceData.get("lowPrice")))
                        .volume(toLong(priceData.get("volume")))
                        .currency(currency)
                        .build();
                    
                    stockPriceRepository.save(stockPrice);
                    savedCount++;
                    
                } catch (Exception e) {
                    log.warn("종가 저장 실패: stockCode={}, priceData={}, error={}", 
                        stockCode, priceData, e.getMessage());
                }
            }
            
            log.info("과거 종가 데이터 채우기 완료: stockCode={}, ticker={}, 신규저장={}, 업데이트={}, 건너뜀={}", 
                stockCode, ticker, savedCount, updatedCount, skippedCount);
            
            return savedCount + updatedCount;
            
        } catch (Exception e) {
            log.error("과거 종가 데이터 채우기 실패: stockCode={}, ticker={}, startDate={}, endDate={}, error={}", 
                stockCode, ticker, startDate, endDate, e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * 특정 사용자의 모든 보유 종목에 대해 과거 종가 데이터 채우기
     * 각 종목의 첫 매수일부터 오늘까지 종가 정보 조회
     * 종가 정보는 종목별 공유 데이터이므로, 보유 기간과 관계없이 첫 매수일부터 오늘까지 전부 조회
     * 
     * @param userId 사용자 ID
     * @return 총 저장된 종가 데이터 개수
     */
    @Transactional
    public int fillAllHistoricalPricesForUser(Long userId) {
        log.info("사용자 {}의 모든 보유 종목 과거 종가 데이터 채우기 시작", userId);
        
        // 사용자의 모든 매수 기록 조회
        List<InvestmentRecord> buyRecords = investmentRecordRepository.findAll()
            .stream()
            .filter(record -> record.getUser().getId().equals(userId)
                && record.getType() == InvestmentRecord.InvestmentType.BUY
                && record.getAssetType() == AssetType.STOCK
                && record.getStockCode() != null && !record.getStockCode().isEmpty()
                && !record.getIsDeleted())
            .collect(Collectors.toList());
        
        // 종목별 첫 매수일 계산
        Map<String, LocalDate> firstBuyDateByStock = new HashMap<>();
        for (InvestmentRecord record : buyRecords) {
            String stockCode = record.getStockCode();
            LocalDate recordDate = record.getRecordDate();
            
            if (!firstBuyDateByStock.containsKey(stockCode) || 
                recordDate.isBefore(firstBuyDateByStock.get(stockCode))) {
                firstBuyDateByStock.put(stockCode, recordDate);
            }
        }
        
        log.info("보유 종목 수: {}, 종목별 첫 매수일: {}", firstBuyDateByStock.size(), firstBuyDateByStock);
        
        int totalSaved = 0;
        LocalDate today = LocalDate.now();
        
        for (Map.Entry<String, LocalDate> entry : firstBuyDateByStock.entrySet()) {
            String stockCode = entry.getKey();
            LocalDate startDate = entry.getValue();
            
            try {
                // 첫 매수일부터 오늘까지 종가 정보 조회
                int saved = fillHistoricalPrices(stockCode, startDate, today);
                totalSaved += saved;
                
                sleepYahooBatchDelay();
                
            } catch (Exception e) {
                log.error("종목 {} 과거 종가 채우기 실패: {}", stockCode, e.getMessage());
            }
        }
        
        log.info("사용자 {}의 모든 보유 종목 과거 종가 데이터 채우기 완료: 총 {} 건 저장", userId, totalSaved);
        return totalSaved;
    }
    
    /**
     * Object를 BigDecimal로 변환
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        if (value instanceof String) return new BigDecimal((String) value);
        return null;
    }
    
    /**
     * Object를 Long으로 변환
     */
    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) return Long.parseLong((String) value);
        return null;
    }

    private void sleepYahooBatchDelay() {
        int ms = Math.max(0, yahooBatchDelayMs);
        if (ms == 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

