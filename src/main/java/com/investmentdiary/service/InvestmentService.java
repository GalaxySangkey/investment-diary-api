package com.investmentdiary.service;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.investment.BuyInvestmentRequest;
import com.investmentdiary.dto.investment.SellInvestmentRequest;
import com.investmentdiary.entity.AssetType;
import com.investmentdiary.entity.InvestmentRecord;
import com.investmentdiary.entity.User;
import com.investmentdiary.exception.InvestmentNotFoundException;
import com.investmentdiary.exception.UserNotFoundException;
import com.investmentdiary.repository.InvestmentRecordRepository;
import com.investmentdiary.repository.UserRepository;
import com.investmentdiary.repository.StockDividendRepository;
import com.investmentdiary.repository.StockTickerMappingRepository;
import com.investmentdiary.repository.PortfolioSettingsRepository;
import com.investmentdiary.repository.ExchangeRateRepository;
import com.investmentdiary.entity.PortfolioSettings;
import com.investmentdiary.entity.ExchangeRate;
import com.investmentdiary.util.TickerConverter;
import com.investmentdiary.service.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import com.investmentdiary.entity.StockDividend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional(readOnly = true)
public class InvestmentService {
    
    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);
    
    private final InvestmentRecordRepository investmentRecordRepository;
    private final UserRepository userRepository;
    private final StockDividendRepository stockDividendRepository;
    private final StockTickerMappingRepository tickerMappingRepository;
    private final TickerConverter tickerConverter;
    private final PortfolioSettingsRepository portfolioSettingsRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final StockDataBatchService stockDataBatchService;
    private final YahooFinanceService yahooFinanceService;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public InvestmentService(InvestmentRecordRepository investmentRecordRepository,
                           UserRepository userRepository,
                           StockDividendRepository stockDividendRepository,
                           StockTickerMappingRepository tickerMappingRepository,
                           TickerConverter tickerConverter,
                           PortfolioSettingsRepository portfolioSettingsRepository,
                           ExchangeRateRepository exchangeRateRepository,
                           StockDataBatchService stockDataBatchService,
                           YahooFinanceService yahooFinanceService) {
        this.investmentRecordRepository = investmentRecordRepository;
        this.userRepository = userRepository;
        this.stockDividendRepository = stockDividendRepository;
        this.tickerMappingRepository = tickerMappingRepository;
        this.tickerConverter = tickerConverter;
        this.portfolioSettingsRepository = portfolioSettingsRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.stockDataBatchService = stockDataBatchService;
        this.yahooFinanceService = yahooFinanceService;
    }
    
    /**
     * 투자 기록 목록 조회 (페이징)
     */
    public ApiResponse<Page<InvestmentRecord>> getInvestments(Long userId, Pageable pageable) {
        log.info("사용자 {}의 투자 기록 조회", userId);
        
        Page<InvestmentRecord> investments = investmentRecordRepository.findByUserId(userId, pageable);
        
        // 각 투자 기록에 최신 배당 정보 업데이트
        investments.getContent().forEach(record -> {
            if (record.getType() == InvestmentRecord.InvestmentType.BUY 
                && record.getAssetType() == AssetType.STOCK
                && record.getStockCode() != null && !record.getStockCode().isEmpty()) {
                DividendInfo dividendInfo = getDividendInfoFromDB(record.getStockCode());
                if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                    record.setDividendPerShare(dividendInfo.dividendPerShare);
                    record.setDividendDate(dividendInfo.dividendDate);
                    record.calculateDividendRatio();
                }
            }
        });
        
        return ApiResponse.success(investments, "투자 기록을 성공적으로 조회했습니다.");
    }
    
    /**
     * 투자 기록 검색
     */
    public ApiResponse<Page<InvestmentRecord>> searchInvestments(
            Long userId,
            InvestmentRecord.InvestmentType type,
            String stockName,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        
        log.info("사용자 {}의 투자 기록 검색", userId);
        
        Page<InvestmentRecord> investments = investmentRecordRepository.searchRecords(
            userId, type, stockName, startDate, endDate, pageable);
        
        // 각 투자 기록에 최신 배당 정보 업데이트
        investments.getContent().forEach(record -> {
            if (record.getType() == InvestmentRecord.InvestmentType.BUY 
                && record.getAssetType() == AssetType.STOCK
                && record.getStockCode() != null && !record.getStockCode().isEmpty()) {
                DividendInfo dividendInfo = getDividendInfoFromDB(record.getStockCode());
                if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                    record.setDividendPerShare(dividendInfo.dividendPerShare);
                    record.setDividendDate(dividendInfo.dividendDate);
                    record.calculateDividendRatio();
                }
            }
        });
        
        return ApiResponse.success(investments, "투자 기록 검색이 완료되었습니다.");
    }
    
    /**
     * 투자 기록 상세 조회
     */
    public ApiResponse<InvestmentRecord> getInvestment(Long userId, Long investmentId) {
        log.info("투자 기록 상세 조회: {}", investmentId);
        
        InvestmentRecord investment = investmentRecordRepository.findById(investmentId)
            .orElseThrow(() -> new InvestmentNotFoundException("투자 기록을 찾을 수 없습니다."));
        
        // 본인의 투자 기록만 조회 가능
        if (!investment.getUser().getId().equals(userId)) {
            throw new InvestmentNotFoundException("접근 권한이 없습니다.");
        }
        
        // 최신 배당 정보 업데이트
        if (investment.getType() == InvestmentRecord.InvestmentType.BUY 
            && investment.getAssetType() == AssetType.STOCK
            && investment.getStockCode() != null && !investment.getStockCode().isEmpty()) {
            DividendInfo dividendInfo = getDividendInfoFromDB(investment.getStockCode());
            if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                investment.setDividendPerShare(dividendInfo.dividendPerShare);
                investment.setDividendDate(dividendInfo.dividendDate);
                investment.calculateDividendRatio();
            }
        }
        
        return ApiResponse.success(investment, "투자 기록을 성공적으로 조회했습니다.");
    }
    
    /**
     * 매수 기록 생성
     */
    @Transactional
    public ApiResponse<InvestmentRecord> createBuyInvestment(Long userId, BuyInvestmentRequest request) {
        AssetType assetType = request.getAssetType() != null ? request.getAssetType() : AssetType.STOCK;
        
        // 자산 유형에 따른 검증
        if (assetType == AssetType.STOCK) {
            // 주식일 때: stockName 필수
            if (request.getStockName() == null || request.getStockName().trim().isEmpty()) {
                throw new IllegalArgumentException("주식 거래는 종목명이 필수입니다.");
            }
        } else if (assetType == AssetType.CURRENCY) {
            // 외환일 때: currencyPair 또는 (baseCurrency + quoteCurrency) 필수, exchangeRate 필수
            boolean hasCurrencyPair = request.getCurrencyPair() != null && !request.getCurrencyPair().trim().isEmpty();
            boolean hasBaseAndQuote = request.getBaseCurrency() != null && !request.getBaseCurrency().trim().isEmpty() 
                                    && request.getQuoteCurrency() != null && !request.getQuoteCurrency().trim().isEmpty();
            
            if (!hasCurrencyPair && !hasBaseAndQuote) {
                throw new IllegalArgumentException("외환 거래는 통화쌍 또는 기준통화/상대통화가 필수입니다.");
            }
            
            if (request.getExchangeRate() == null) {
                throw new IllegalArgumentException("외환 거래는 환율이 필수입니다.");
            }
            
            // stockName은 통화쌍으로 설정 (없으면 생성)
            if (request.getStockName() == null || request.getStockName().trim().isEmpty()) {
                if (hasCurrencyPair) {
                    request.setStockName(request.getCurrencyPair());
                } else {
                    request.setStockName(request.getBaseCurrency() + "/" + request.getQuoteCurrency());
                }
            }
        }
        
        log.info("사용자 {}의 매수 기록 생성: assetType={}, name={}", userId, assetType, 
                assetType == AssetType.STOCK ? request.getStockName() : request.getCurrencyPair());
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 주식인 경우 배당 정보 자동 조회 (DB에서 먼저 조회, 없으면 실시간 API 조회)
        BigDecimal dividendPerShare = request.getDividendPerShare();
        LocalDate dividendDate = null;
        if (assetType == AssetType.STOCK && request.getStockCode() != null && !request.getStockCode().isEmpty()) {
            // 사용자가 수동으로 입력한 배당 정보가 없으면 DB에서 조회
            if (dividendPerShare == null) {
                DividendInfo dividendInfo = getDividendInfoFromDB(request.getStockCode());
                if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                    dividendPerShare = dividendInfo.dividendPerShare;
                    dividendDate = dividendInfo.dividendDate;
                    log.info("DB에서 배당 정보 조회 성공: stockCode={}, dividendPerShare={}, dividendDate={}", 
                        request.getStockCode(), dividendPerShare, dividendDate);
                    if (dividendDate == null) {
                        log.warn("배당일이 null입니다: stockCode={}, dividendPerShare={}", 
                            request.getStockCode(), dividendPerShare);
                    }
                } else {
                    log.debug("DB에 배당 정보가 없습니다. 실시간 API 조회 시도: stockCode={}", request.getStockCode());
                    // DB에 없으면 실시간 API 조회 시도
                    dividendInfo = getDividendInfoFromAPI(request.getStockCode());
                    if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                        dividendPerShare = dividendInfo.dividendPerShare;
                        dividendDate = dividendInfo.dividendDate;
                        log.info("실시간 API에서 배당 정보 조회 성공: stockCode={}, dividendPerShare={}, dividendDate={}", 
                            request.getStockCode(), dividendPerShare, dividendDate);
                        
                        // API에서 조회한 배당 정보를 DB에 저장 (비동기)
                        // 배당 정보는 나중에 배치 작업으로 업데이트되지만, 즉시 사용할 수 있도록 저장
                        try {
                            saveDividendInfoToDB(request.getStockCode(), dividendPerShare, dividendDate);
                        } catch (Exception e) {
                            log.warn("배당 정보 DB 저장 실패 (무시): stockCode={}, error={}", request.getStockCode(), e.getMessage());
                        }
                    } else {
                        log.debug("실시간 API에서도 배당 정보를 조회하지 못했습니다: stockCode={}", request.getStockCode());
                    }
                }
            }
        }
        
        // 투자 비율 자동 계산 (사용자가 입력한 값이 없거나 0인 경우)
        BigDecimal investmentRatio = request.getInvestmentRatio();
        if (investmentRatio == null || investmentRatio.compareTo(BigDecimal.ZERO) == 0) {
            investmentRatio = calculateInvestmentRatio(userId, assetType, 
                request.getPricePerShare(), request.getQuantity(), 
                request.getExchangeRate(), request.getStockCode());
            log.info("투자 비율 자동 계산: userId={}, investmentRatio={}%", userId, investmentRatio);
        }
        
        // 투자 기록 생성
        InvestmentRecord investment = InvestmentRecord.builder()
            .user(user)
            .recordDate(request.getRecordDate())
            .type(InvestmentRecord.InvestmentType.BUY)
            .assetType(assetType)
            .stockName(request.getStockName())
            .stockCode(request.getStockCode())
            .currencyPair(request.getCurrencyPair())
            .baseCurrency(request.getBaseCurrency())
            .quoteCurrency(request.getQuoteCurrency())
            .exchangeRate(request.getExchangeRate())
            .investmentRatio(investmentRatio)
            .quantity(request.getQuantity())
            .pricePerShare(request.getPricePerShare())
            .dividendPerShare(dividendPerShare)
            .dividendDate(dividendDate)
            .buyReason(request.getBuyReason())
            .build();
        
        // 계산된 값들 설정
        investment.calculateTotalAmount();
        investment.calculateDividendRatio();
        
        InvestmentRecord savedInvestment = investmentRecordRepository.save(investment);
        
        log.info("매수 기록 생성 완료: {}", savedInvestment.getId());
        
        // 주식인 경우 해당 매수일부터 오늘까지 종가 데이터 채우기 (비동기)
        // 종가 정보는 종목별 공유 데이터이므로, 매수일부터 오늘까지 전부 조회해두는 것이 효율적
        if (assetType == AssetType.STOCK && request.getStockCode() != null && !request.getStockCode().isEmpty()) {
            try {
                LocalDate buyDate = request.getRecordDate();
                String stockCode = request.getStockCode();
                LocalDate today = LocalDate.now();
                
                // 비동기로 처리하여 사용자 응답 속도 유지
                new Thread(() -> {
                    try {
                        log.info("종목 {} 과거 종가 데이터 채우기 시작 (비동기): {} ~ 오늘", stockCode, buyDate);
                        // 1. 과거 종가 데이터 채우기
                        int savedCount = stockDataBatchService.fillHistoricalPricesForStock(stockCode, buyDate);
                        log.info("종목 {} 과거 종가 데이터 채우기 완료: {} 건 저장", stockCode, savedCount);
                        
                        // 2. 오늘 날짜의 현재가도 업데이트 (과거 데이터에 오늘이 포함되어 있어도 최신 정보로 업데이트)
                        try {
                            log.info("종목 {} 오늘 날짜 현재가 업데이트 시작 (비동기)", stockCode);
                            stockDataBatchService.saveStockPriceForTicker(stockCode, today);
                            log.info("종목 {} 오늘 날짜 현재가 업데이트 완료", stockCode);
                        } catch (Exception e) {
                            log.warn("종목 {} 오늘 날짜 현재가 업데이트 실패 (무시): {}", stockCode, e.getMessage());
                            // 현재가 업데이트 실패는 과거 데이터 채우기와 별개로 처리
                        }
                    } catch (Exception e) {
                        log.error("종목 {} 과거 종가 데이터 채우기 실패: {}", stockCode, e.getMessage(), e);
                    }
                }).start();
            } catch (Exception e) {
                log.warn("과거 종가 데이터 채우기 스레드 시작 실패 (무시): {}", e.getMessage());
                // 비동기 처리 실패는 사용자 응답에 영향 없음
            }
        }
        
        return ApiResponse.success(savedInvestment, "매수 기록이 성공적으로 생성되었습니다.");
    }
    
    /**
     * 매도 기록 생성
     */
    @Transactional
    public ApiResponse<InvestmentRecord> createSellInvestment(Long userId, SellInvestmentRequest request) {
        log.info("사용자 {}의 매도 기록 생성", userId);
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        
        // 매도할 종목 조회
        InvestmentRecord buyRecord = investmentRecordRepository.findById(request.getSelectedStockId())
            .orElseThrow(() -> new InvestmentNotFoundException("매도할 종목을 찾을 수 없습니다."));
        
        // 본인의 종목만 매도 가능
        if (!buyRecord.getUser().getId().equals(userId)) {
            throw new InvestmentNotFoundException("접근 권한이 없습니다.");
        }
        
        // 매도 날짜 검증: 첫 매수 날짜 이후여야 함
        List<InvestmentRecord> allBuyRecords = investmentRecordRepository.findByUserIdAndType(userId, InvestmentRecord.InvestmentType.BUY);
        List<InvestmentRecord> sameStockBuyRecords = allBuyRecords.stream()
            .filter(r -> buyRecord.getStockName().equals(r.getStockName()))
            .filter(r -> (buyRecord.getStockCode() == null && r.getStockCode() == null) || 
                         (buyRecord.getStockCode() != null && buyRecord.getStockCode().equals(r.getStockCode())))
            .filter(r -> !r.getIsDeleted())
            .sorted((a, b) -> {
                int dateCompare = a.getRecordDate().compareTo(b.getRecordDate());
                if (dateCompare != 0) return dateCompare;
                return Long.compare(a.getId(), b.getId());
            })
            .collect(java.util.stream.Collectors.toList());
        
        if (!sameStockBuyRecords.isEmpty()) {
            LocalDate firstBuyDate = sameStockBuyRecords.get(0).getRecordDate();
            if (request.getRecordDate().isBefore(firstBuyDate)) {
                throw new IllegalArgumentException(
                    String.format("매도 날짜(%s)는 첫 매수 날짜(%s) 이후여야 합니다.", 
                        request.getRecordDate(), firstBuyDate));
            }
        }
        
        // 매도 수량 검증: 보유 수량을 초과할 수 없음
        BigDecimal holdingQuantity = calculateStockHoldingQuantity(userId, buyRecord.getStockName(), buyRecord.getStockCode());
        if (holdingQuantity == null || holdingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("매도할 수 있는 보유 수량이 없습니다.");
        }
        if (request.getSellQuantity() != null && request.getSellQuantity().compareTo(holdingQuantity) > 0) {
            throw new IllegalArgumentException(
                String.format("매도 수량(%s)이 보유 수량(%s)을 초과할 수 없습니다.", 
                    request.getSellQuantity(), holdingQuantity));
        }
        if (request.getSellQuantity() != null && request.getSellQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("매도 수량은 0보다 커야 합니다.");
        }
        
        // 매도 비율 자동 계산 (매도 수량이 있고 매도 비율이 없는 경우)
        // 같은 종목의 전체 보유 수량 기준으로 계산
        BigDecimal sellRatio = request.getSellRatio();
        if (sellRatio == null && request.getSellQuantity() != null) {
            // 위에서 이미 계산한 holdingQuantity 사용
            if (holdingQuantity != null && holdingQuantity.compareTo(BigDecimal.ZERO) > 0) {
                // 매도 비율 = (매도 수량 / 보유 수량) * 100
                sellRatio = request.getSellQuantity()
                    .divide(holdingQuantity, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                // 0.01% ~ 100% 범위로 제한
                if (sellRatio.compareTo(BigDecimal.valueOf(0.01)) < 0) {
                    sellRatio = BigDecimal.valueOf(0.01);
                } else if (sellRatio.compareTo(BigDecimal.valueOf(100)) > 0) {
                    sellRatio = BigDecimal.valueOf(100);
                }
                
                log.info("매도 비율 자동 계산: sellQuantity={}, holdingQuantity={}, sellRatio={}%", 
                    request.getSellQuantity(), holdingQuantity, sellRatio);
            }
        }
        
        // 매도 기록 생성
        // 매도 기록의 investmentRatio는 매수 기록의 investmentRatio를 기반으로 매도 비율을 적용하여 계산
        BigDecimal sellInvestmentRatio = BigDecimal.ZERO;
        if (buyRecord.getInvestmentRatio() != null && sellRatio != null) {
            // 매수 기록의 투자 비율 * 매도 비율(%) / 100
            sellInvestmentRatio = buyRecord.getInvestmentRatio()
                .multiply(sellRatio)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        
        InvestmentRecord sellRecord = InvestmentRecord.builder()
            .user(user)
            .recordDate(request.getRecordDate())
            .type(InvestmentRecord.InvestmentType.SELL)
            .stockName(buyRecord.getStockName())
            .stockCode(buyRecord.getStockCode())
            .investmentRatio(sellInvestmentRatio) // 매도 기록의 투자 비율 설정
            .selectedStockId(buyRecord.getId())
            .sellQuantity(request.getSellQuantity())
            .sellRatio(sellRatio)
            .sellPrice(request.getSellPrice())
            .realizedProfitRate(request.getRealizedProfitRate())
            .sellReason(request.getSellReason())
            .build();
        
        // 실현 손익 계산
        // 매도 시점의 평단가 계산 (매도일 이전의 매수 기록만 고려)
        BigDecimal avgBuyPrice = calculateAverageBuyPriceAtDate(userId, buyRecord.getStockName(), buyRecord.getStockCode(), request.getRecordDate(), null);
        
        // 평단가가 없으면 개별 매수 기록의 단가를 사용 (fallback)
        if (avgBuyPrice == null || avgBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            if (buyRecord.getPricePerShare() != null && buyRecord.getPricePerShare().compareTo(BigDecimal.ZERO) > 0) {
                avgBuyPrice = buyRecord.getPricePerShare();
                log.warn("평단가 계산 실패, 개별 매수 기록 단가 사용: stockName={}, pricePerShare={}", 
                    buyRecord.getStockName(), avgBuyPrice);
            } else {
                log.warn("평단가 계산 실패 및 개별 매수 기록 단가도 없음: stockName={}, buyRecordId={}", 
                    buyRecord.getStockName(), buyRecord.getId());
            }
        }
        
        // 매도금액(sellPrice)이 있으면 평단가 기준으로 손익률 계산
        // 사용자가 명시적으로 realizedProfitRate를 입력한 경우에는 자동 계산하지 않음
        if (request.getRealizedProfitRate() == null) {
            if (request.getSellPrice() != null && avgBuyPrice != null && avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
                // 매도금액 기준으로 평단가 대비 손익률 계산
                calculateRealizedProfitFromSellPrice(sellRecord, avgBuyPrice, request.getSellPrice());
            } else if (request.getSellPrice() == null) {
                // 기존 로직 (평단가 기준, 현재 가격 사용)
                calculateRealizedProfitWithAvgPrice(sellRecord, buyRecord, avgBuyPrice);
            } else {
                log.warn("실현 손익 계산 불가: sellPrice={}, avgBuyPrice={}", request.getSellPrice(), avgBuyPrice);
            }
        }
        
        InvestmentRecord savedSellRecord = investmentRecordRepository.save(sellRecord);
        
        log.info("매도 기록 생성 완료: {}", savedSellRecord.getId());
        
        return ApiResponse.success(savedSellRecord, "매도 기록이 성공적으로 생성되었습니다.");
    }
    
    /**
     * 투자 기록 수정
     */
    @Transactional
    public ApiResponse<InvestmentRecord> updateInvestment(Long userId, Long investmentId, Map<String, Object> updates) {
        log.info("투자 기록 수정: {}", investmentId);
        
        InvestmentRecord investment = investmentRecordRepository.findById(investmentId)
            .orElseThrow(() -> new InvestmentNotFoundException("투자 기록을 찾을 수 없습니다."));
        
        // 본인의 투자 기록만 수정 가능
        if (!investment.getUser().getId().equals(userId)) {
            throw new InvestmentNotFoundException("접근 권한이 없습니다.");
        }
        
        // 수정 가능한 필드들 업데이트
        if (updates.containsKey("assetType")) {
            investment.setAssetType(AssetType.valueOf(updates.get("assetType").toString()));
        }
        if (updates.containsKey("stockName")) {
            investment.setStockName((String) updates.get("stockName"));
        }
        if (updates.containsKey("stockCode")) {
            investment.setStockCode((String) updates.get("stockCode"));
        }
        if (updates.containsKey("currencyPair")) {
            investment.setCurrencyPair((String) updates.get("currencyPair"));
        }
        if (updates.containsKey("baseCurrency")) {
            investment.setBaseCurrency((String) updates.get("baseCurrency"));
        }
        if (updates.containsKey("quoteCurrency")) {
            investment.setQuoteCurrency((String) updates.get("quoteCurrency"));
        }
        if (updates.containsKey("exchangeRate")) {
            investment.setExchangeRate(new BigDecimal(updates.get("exchangeRate").toString()));
        }
        if (updates.containsKey("investmentRatio")) {
            investment.setInvestmentRatio(new BigDecimal(updates.get("investmentRatio").toString()));
        }
        if (updates.containsKey("quantity")) {
            Object qtyValue = updates.get("quantity");
            if (qtyValue instanceof BigDecimal) {
                investment.setQuantity((BigDecimal) qtyValue);
            } else if (qtyValue instanceof Number) {
                investment.setQuantity(BigDecimal.valueOf(((Number) qtyValue).doubleValue()));
            } else {
                investment.setQuantity(new BigDecimal(qtyValue.toString()));
            }
        }
        if (updates.containsKey("pricePerShare")) {
            investment.setPricePerShare(new BigDecimal(updates.get("pricePerShare").toString()));
        }
        if (updates.containsKey("totalAmount")) {
            investment.setTotalAmount(new BigDecimal(updates.get("totalAmount").toString()));
        }
        if (updates.containsKey("buyReason")) {
            investment.setBuyReason((String) updates.get("buyReason"));
        }
        if (updates.containsKey("sellReason")) {
            investment.setSellReason((String) updates.get("sellReason"));
        }
        if (updates.containsKey("sellQuantity")) {
            investment.setSellQuantity((BigDecimal) updates.get("sellQuantity"));
        }
        if (updates.containsKey("sellRatio")) {
            investment.setSellRatio(new BigDecimal(updates.get("sellRatio").toString()));
        }
        if (updates.containsKey("realizedProfitRate")) {
            investment.setRealizedProfitRate(new BigDecimal(updates.get("realizedProfitRate").toString()));
        }
        if (updates.containsKey("sellPrice")) {
            investment.setSellPrice(new BigDecimal(updates.get("sellPrice").toString()));
        }
        
        // 계산된 값들 재계산
        if (investment.isBuyRecord()) {
            investment.calculateTotalAmount();
            // 매수 기록 수정 시 배당 정보도 업데이트
            if (investment.getStockCode() != null && !investment.getStockCode().isEmpty()) {
                DividendInfo dividendInfo = getDividendInfoFromDB(investment.getStockCode());
                if (dividendInfo == null || dividendInfo.dividendPerShare == null) {
                    // DB에 없으면 실시간 API 조회
                    dividendInfo = getDividendInfoFromAPI(investment.getStockCode());
                }
                if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                    investment.setDividendPerShare(dividendInfo.dividendPerShare);
                    investment.setDividendDate(dividendInfo.dividendDate);
                    log.info("수정 모드: 배당 정보 업데이트: stockCode={}, dividendPerShare={}, dividendDate={}", 
                        investment.getStockCode(), dividendInfo.dividendPerShare, dividendInfo.dividendDate);
                    
                    // API에서 조회한 배당 정보를 DB에 저장 (비동기)
                    try {
                        saveDividendInfoToDB(investment.getStockCode(), dividendInfo.dividendPerShare, dividendInfo.dividendDate);
                    } catch (Exception e) {
                        log.warn("배당 정보 DB 저장 실패 (무시): stockCode={}, error={}", investment.getStockCode(), e.getMessage());
                    }
                }
            }
            investment.calculateDividendRatio();
        } else if (investment.isSellRecord()) {
            // 매도 기록 수정 시 항상 실현 손익 재계산 (매도 시점의 평단가 기준)
            // 단, 사용자가 명시적으로 realizedProfitRate를 입력한 경우에는 자동 계산하지 않음
            if (!updates.containsKey("realizedProfitRate")) {
                // 매도 시점의 평단가 계산 (매도일 이전의 기록만 고려, 현재 수정 중인 매도 기록 제외)
                BigDecimal avgBuyPrice = calculateAverageBuyPriceAtDate(userId, investment.getStockName(), investment.getStockCode(), investment.getRecordDate(), investment.getId());
                InvestmentRecord buyRecord = null;
                if (investment.getSelectedStockId() != null) {
                    buyRecord = investmentRecordRepository.findById(investment.getSelectedStockId())
                        .orElse(null);
                }
                
                // 평단가가 없으면 개별 매수 기록의 단가를 사용 (fallback)
                if (avgBuyPrice == null || avgBuyPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    if (buyRecord != null && buyRecord.getPricePerShare() != null && buyRecord.getPricePerShare().compareTo(BigDecimal.ZERO) > 0) {
                        avgBuyPrice = buyRecord.getPricePerShare();
                        log.warn("수정 모드: 평단가 계산 실패, 개별 매수 기록 단가 사용: stockName={}, pricePerShare={}", 
                            investment.getStockName(), avgBuyPrice);
                    } else {
                        log.warn("수정 모드: 평단가 계산 실패 및 개별 매수 기록 단가도 없음: stockName={}", 
                            investment.getStockName());
                    }
                }
                
                // sellPrice가 있으면 매도금액 기준으로 계산, 없으면 평단가 기준으로 계산
                BigDecimal sellPrice = investment.getSellPrice();
                if (sellPrice != null && sellPrice.compareTo(BigDecimal.ZERO) > 0 && avgBuyPrice != null && avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
                    // 매도금액 기준으로 계산
                    calculateRealizedProfitFromSellPrice(investment, avgBuyPrice, sellPrice);
                } else if (buyRecord != null && avgBuyPrice != null && avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
                    // 기존 로직 (평단가 기준)
                    calculateRealizedProfitWithAvgPrice(investment, buyRecord, avgBuyPrice);
                } else {
                    log.warn("수정 모드: 실현 손익 계산 불가: sellPrice={}, avgBuyPrice={}", sellPrice, avgBuyPrice);
                }
            }
        }
        
        InvestmentRecord updatedInvestment = investmentRecordRepository.save(investment);
        
        log.info("투자 기록 수정 완료: {}", updatedInvestment.getId());
        
        // 주식인 경우 해당 매수일부터 오늘까지 종가 데이터 채우기 (비동기)
        // 수정 시에도 과거 종가 데이터가 없으면 업데이트
        if (updatedInvestment.isBuyRecord() 
            && updatedInvestment.getAssetType() == AssetType.STOCK 
            && updatedInvestment.getStockCode() != null 
            && !updatedInvestment.getStockCode().isEmpty()) {
            try {
                LocalDate buyDate = updatedInvestment.getRecordDate();
                String stockCode = updatedInvestment.getStockCode();
                LocalDate today = LocalDate.now();
                
                // 비동기로 처리하여 사용자 응답 속도 유지
                new Thread(() -> {
                    try {
                        log.info("수정 모드: 종목 {} 과거 종가 데이터 채우기 시작 (비동기): {} ~ 오늘", stockCode, buyDate);
                        // 1. 과거 종가 데이터 채우기
                        int savedCount = stockDataBatchService.fillHistoricalPricesForStock(stockCode, buyDate);
                        log.info("수정 모드: 종목 {} 과거 종가 데이터 채우기 완료: {} 건 저장", stockCode, savedCount);
                        
                        // 2. 오늘 날짜의 현재가도 업데이트 (과거 데이터에 오늘이 포함되어 있어도 최신 정보로 업데이트)
                        try {
                            log.info("수정 모드: 종목 {} 오늘 날짜 현재가 업데이트 시작 (비동기)", stockCode);
                            stockDataBatchService.saveStockPriceForTicker(stockCode, today);
                            log.info("수정 모드: 종목 {} 오늘 날짜 현재가 업데이트 완료", stockCode);
                        } catch (Exception e) {
                            log.warn("수정 모드: 종목 {} 오늘 날짜 현재가 업데이트 실패 (무시): {}", stockCode, e.getMessage());
                            // 현재가 업데이트 실패는 과거 데이터 채우기와 별개로 처리
                        }
                    } catch (Exception e) {
                        log.error("수정 모드: 종목 {} 과거 종가 데이터 채우기 실패: {}", stockCode, e.getMessage(), e);
                    }
                }).start();
            } catch (Exception e) {
                log.warn("수정 모드: 과거 종가 데이터 채우기 스레드 시작 실패 (무시): {}", e.getMessage());
                // 비동기 처리 실패는 사용자 응답에 영향 없음
            }
        }
        
        return ApiResponse.success(updatedInvestment, "투자 기록이 성공적으로 수정되었습니다.");
    }
    
    /**
     * 투자 기록 삭제 (소프트 삭제)
     */
    @Transactional
    public ApiResponse<Void> deleteInvestment(Long userId, Long investmentId) {
        log.info("투자 기록 삭제: {}", investmentId);
        
        InvestmentRecord investment = investmentRecordRepository.findById(investmentId)
            .orElseThrow(() -> new InvestmentNotFoundException("투자 기록을 찾을 수 없습니다."));
        
        // 본인의 투자 기록만 삭제 가능
        if (!investment.getUser().getId().equals(userId)) {
            throw new InvestmentNotFoundException("접근 권한이 없습니다.");
        }
        
        investment.softDelete();
        investmentRecordRepository.save(investment);
        
        log.info("투자 기록 삭제 완료: {}", investmentId);
        
        return ApiResponse.success(null, "투자 기록이 성공적으로 삭제되었습니다.");
    }
    
    /**
     * 특정 날짜 투자 기록 조회
     */
    public ApiResponse<List<InvestmentRecord>> getInvestmentsByDate(Long userId, LocalDate date) {
        log.info("사용자 {}의 {} 투자 기록 조회", userId, date);
        
        List<InvestmentRecord> investments = investmentRecordRepository
            .findByUserIdAndRecordDate(userId, date);
        
        // 각 투자 기록에 최신 배당 정보 업데이트
        investments.forEach(record -> {
            if (record.getType() == InvestmentRecord.InvestmentType.BUY 
                && record.getAssetType() == AssetType.STOCK
                && record.getStockCode() != null && !record.getStockCode().isEmpty()) {
                DividendInfo dividendInfo = getDividendInfoFromDB(record.getStockCode());
                if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                    record.setDividendPerShare(dividendInfo.dividendPerShare);
                    record.setDividendDate(dividendInfo.dividendDate);
                    record.calculateDividendRatio();
                }
            }
        });
        
        return ApiResponse.success(investments, "해당 날짜의 투자 기록을 조회했습니다.");
    }
    
    /**
     * 월별 투자 기록 조회
     */
    public ApiResponse<List<InvestmentRecord>> getInvestmentsByMonth(Long userId, int year, int month) {
        log.info("사용자 {}의 {}-{} 투자 기록 조회", userId, year, month);
        
        List<InvestmentRecord> investments = investmentRecordRepository
            .findRecordsByYearAndMonth(userId, year, month);
        
        // 각 투자 기록에 최신 배당 정보 업데이트
        investments.forEach(record -> {
            if (record.getType() == InvestmentRecord.InvestmentType.BUY 
                && record.getAssetType() == AssetType.STOCK
                && record.getStockCode() != null && !record.getStockCode().isEmpty()) {
                DividendInfo dividendInfo = getDividendInfoFromDB(record.getStockCode());
                if (dividendInfo != null && dividendInfo.dividendPerShare != null) {
                    record.setDividendPerShare(dividendInfo.dividendPerShare);
                    record.setDividendDate(dividendInfo.dividendDate);
                    record.calculateDividendRatio();
                }
            }
        });
        
        return ApiResponse.success(investments, "해당 월의 투자 기록을 조회했습니다.");
    }
    
    /**
     * 수익률별 투자 기록 조회
     */
    public ApiResponse<List<InvestmentRecord>> getProfitableInvestments(Long userId, BigDecimal minProfitRate) {
        log.info("사용자 {}의 수익률 {}% 이상 투자 기록 조회", userId, minProfitRate);
        
        List<InvestmentRecord> investments = investmentRecordRepository
            .findProfitableRecords(userId, minProfitRate);
        
        return ApiResponse.success(investments, "수익률 기준 투자 기록을 조회했습니다.");
    }
    
    /**
     * 손실 투자 기록 조회
     */
    public ApiResponse<List<InvestmentRecord>> getLossInvestments(Long userId) {
        log.info("사용자 {}의 손실 투자 기록 조회", userId);
        
        List<InvestmentRecord> investments = investmentRecordRepository
            .findLossRecords(userId);
        
        return ApiResponse.success(investments, "손실 투자 기록을 조회했습니다.");
    }
    
    /**
     * DB에서 종목코드로 배당 정보 조회
     * @param stockCode 종목코드
     * @return 주당 배당금 (없으면 null)
     */
    private BigDecimal getDividendPerShareFromDB(String stockCode) {
        DividendInfo info = getDividendInfoFromDB(stockCode);
        return info != null ? info.dividendPerShare : null;
    }
    
    /**
     * 배당 정보를 담는 내부 클래스
     */
    private static class DividendInfo {
        BigDecimal dividendPerShare;
        LocalDate dividendDate;
        
        DividendInfo(BigDecimal dividendPerShare, LocalDate dividendDate) {
            this.dividendPerShare = dividendPerShare;
            this.dividendDate = dividendDate;
        }
    }
    
    /**
     * 실시간 API에서 종목코드로 배당 정보 조회
     * @param stockCode 종목코드
     * @return 배당 정보 (배당금, 배당일)
     */
    private DividendInfo getDividendInfoFromAPI(String stockCode) {
        try {
            // 티커 매핑 조회
            String tickerTemp = tickerMappingRepository.findByStockCode(stockCode)
                .filter(mapping -> mapping.getIsActive())
                .map(mapping -> mapping.getTicker())
                .orElse(null);
            
            // 티커가 없으면 종목코드를 티커로 변환
            if (tickerTemp == null) {
                tickerTemp = tickerConverter.convertToYahooTicker(stockCode, "KOSPI");
            }
            
            if (tickerTemp == null) {
                log.debug("티커 변환 실패: stockCode={}", stockCode);
                return null;
            }
            
            final String ticker = tickerTemp;
            
            // 실시간 API 조회
            Map<String, Object> dividendData = yahooFinanceService.getDividendInfo(ticker);
            if (dividendData == null || dividendData.isEmpty()) {
                log.debug("API에서 배당 정보 조회 실패: stockCode={}, ticker={}", stockCode, ticker);
                return null;
            }
            
            BigDecimal dividendPerShare = null;
            LocalDate dividendDate = null;
            
            if (dividendData.get("annualDividend") != null) {
                dividendPerShare = (BigDecimal) dividendData.get("annualDividend");
            } else if (dividendData.get("dividendPerShare") != null) {
                dividendPerShare = (BigDecimal) dividendData.get("dividendPerShare");
            }
            
            if (dividendData.get("dividendDate") != null) {
                try {
                    dividendDate = LocalDate.parse(dividendData.get("dividendDate").toString());
                } catch (Exception e) {
                    log.warn("배당일 파싱 실패: stockCode={}, dividendDate={}", stockCode, dividendData.get("dividendDate"));
                }
            }
            
            if (dividendPerShare != null) {
                log.info("실시간 API에서 배당 정보 조회 성공: stockCode={}, ticker={}, dividendPerShare={}, dividendDate={}", 
                    stockCode, ticker, dividendPerShare, dividendDate);
                return new DividendInfo(dividendPerShare, dividendDate);
            }
            
            return null;
        } catch (Exception e) {
            log.warn("실시간 API 배당 정보 조회 실패: stockCode={}, error={}", stockCode, e.getMessage());
            return null;
        }
    }
    
    /**
     * DB에서 종목코드로 배당 정보 조회 (작년 총 배당액 합산)
     * @param stockCode 종목코드
     * @return 배당 정보 (작년 총 배당금, 배당일)
     */
    private DividendInfo getDividendInfoFromDB(String stockCode) {
        try {
            // 티커 매핑 조회
            String tickerTemp = tickerMappingRepository.findByStockCode(stockCode)
                .filter(mapping -> mapping.getIsActive())
                .map(mapping -> mapping.getTicker())
                .orElse(null);
            
            // 티커가 없으면 종목코드를 티커로 변환
            if (tickerTemp == null) {
                tickerTemp = tickerConverter.convertToYahooTicker(stockCode, "KOSPI");
            }
            
            if (tickerTemp == null) {
                return null;
            }
            
            // 람다 표현식에서 사용하기 위해 final 변수로 저장
            final String ticker = tickerTemp;
            
            // 작년 배당 총액 계산
            LocalDate today = LocalDate.now();
            int lastYear = today.getYear() - 1;
            LocalDate lastYearStart = LocalDate.of(lastYear, 1, 1);
            LocalDate lastYearEnd = LocalDate.of(lastYear, 12, 31);
            
            // 작년의 모든 배당 기록 조회
            List<StockDividend> lastYearDividends = stockDividendRepository
                .findByTickerAndDividendDateBetween(ticker, lastYearStart, lastYearEnd);
            
            // 작년 배당 총액 합산 (분기별, 월별 배당 모두 합산)
            BigDecimal totalLastYearDividend = BigDecimal.ZERO;
            LocalDate latestDividendDate = null;
            
            if (!lastYearDividends.isEmpty()) {
                for (StockDividend dividend : lastYearDividends) {
                    // dividendPerShare는 각 배당 지급일의 주당 배당금
                    // 분기별이면 4번, 월별이면 12번 합산됨
                    if (dividend.getDividendPerShare() != null) {
                        totalLastYearDividend = totalLastYearDividend.add(dividend.getDividendPerShare());
                    }
                    // 가장 최근 배당일 저장
                    if (dividend.getDividendDate() != null) {
                        if (latestDividendDate == null || dividend.getDividendDate().isAfter(latestDividendDate)) {
                            latestDividendDate = dividend.getDividendDate();
                        }
                    }
                }
                log.info("작년 배당 총액 계산: stockCode={}, ticker={}, lastYear={}, totalDividend={}, dividendCount={}", 
                    stockCode, ticker, lastYear, totalLastYearDividend, lastYearDividends.size());
            } else {
                // 작년 배당이 없으면 최근 배당 정보 조회 (fallback)
                List<StockDividend> allDividends = stockDividendRepository.findByTickerOrderByDividendDateDesc(ticker);
                if (!allDividends.isEmpty()) {
                    Optional<StockDividend> latestDividend = allDividends.stream()
                        .filter(dividend -> {
                            LocalDate dividendDate = dividend.getDividendDate();
                            if (dividendDate == null) {
                                return false;
                            }
                            return !dividendDate.isAfter(today);
                        })
                        .findFirst();
                    
                    if (latestDividend.isPresent()) {
                        StockDividend dividend = latestDividend.get();
                        // annualDividend가 있으면 연간 배당금 사용, 없으면 dividendPerShare 사용
                        totalLastYearDividend = dividend.getAnnualDividend() != null 
                            ? dividend.getAnnualDividend() 
                            : dividend.getDividendPerShare();
                        latestDividendDate = dividend.getDividendDate();
                        log.info("작년 배당이 없어 최근 배당 사용: stockCode={}, ticker={}, dividendPerShare={}, dividendDate={}", 
                            stockCode, ticker, totalLastYearDividend, latestDividendDate);
                    } else {
                        log.debug("배당 정보가 없습니다: stockCode={}, ticker={}", stockCode, ticker);
                        return null;
                    }
                } else {
                    log.debug("배당 정보가 없습니다: stockCode={}, ticker={}", stockCode, ticker);
                    return null;
                }
            }
            
            if (totalLastYearDividend == null || totalLastYearDividend.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("작년 배당 총액이 0 이하입니다: stockCode={}, ticker={}", stockCode, ticker);
                return null;
            }
            
            return new DividendInfo(totalLastYearDividend, latestDividendDate);
                
        } catch (Exception e) {
            log.warn("배당 정보 조회 실패: stockCode={}, error={}", stockCode, e.getMessage());
            return null;
        }
    }
    
    /**
     * 배당 정보를 DB에 저장 (실시간 API 조회 결과 저장용)
     * 호출하는 메서드가 이미 @Transactional이므로 별도 트랜잭션 불필요
     * @param stockCode 종목코드
     * @param dividendPerShare 배당금
     * @param dividendDate 배당일
     */
    private void saveDividendInfoToDB(String stockCode, BigDecimal dividendPerShare, LocalDate dividendDate) {
        try {
            // 티커 매핑 조회
            String ticker = tickerMappingRepository.findByStockCode(stockCode)
                .filter(mapping -> mapping.getIsActive())
                .map(mapping -> mapping.getTicker())
                .orElse(null);
            
            // 티커가 없으면 종목코드를 티커로 변환
            if (ticker == null) {
                ticker = tickerConverter.convertToYahooTicker(stockCode, "KOSPI");
            }
            
            if (ticker == null) {
                log.debug("티커 변환 실패, 배당 정보 저장 건너뜀: stockCode={}", stockCode);
                return;
            }
            
            // 배당일이 없으면 null로 유지 (배당일 정보가 없을 때는 null로 저장)
            // 배당일이 null인 경우는 연간 배당금 정보만 있는 경우이므로, 
            // 작년 배당 조회 시 fallback 로직에서 처리됨
            
            // 종목명 조회 (기존 배당 정보나 티커 매핑에서)
            String stockName = null;
            // 먼저 기존 배당 정보에서 종목명 조회
            List<StockDividend> existingDividends = stockDividendRepository
                .findByTickerOrderByDividendDateDesc(ticker);
            if (!existingDividends.isEmpty()) {
                stockName = existingDividends.get(0).getStockName();
            }
            // 종목명이 없으면 종목코드를 사용
            if (stockName == null || stockName.trim().isEmpty()) {
                stockName = stockCode;
            }
            
            // 기존 배당 정보 조회
            Optional<StockDividend> existing = stockDividendRepository
                .findByTickerAndDividendDate(ticker, dividendDate);
            
            if (existing.isPresent()) {
                // 기존 배당 정보 업데이트
                StockDividend stockDividend = existing.get();
                stockDividend.setDividendPerShare(dividendPerShare);
                stockDividend.setAnnualDividend(dividendPerShare); // 연간 배당금으로도 저장
                stockDividend.setStockName(stockName);
                stockDividendRepository.save(stockDividend);
                log.debug("기존 배당 정보 업데이트: stockCode={}, ticker={}, dividendPerShare={}, dividendDate={}", 
                    stockCode, ticker, dividendPerShare, dividendDate);
            } else {
                // 새 배당 정보 생성
                StockDividend stockDividend = StockDividend.builder()
                    .ticker(ticker)
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .dividendDate(dividendDate)
                    .dividendPerShare(dividendPerShare)
                    .annualDividend(dividendPerShare) // 연간 배당금으로도 저장
                    .build();
                stockDividendRepository.save(stockDividend);
                log.debug("새 배당 정보 저장: stockCode={}, ticker={}, stockName={}, dividendPerShare={}, dividendDate={}", 
                    stockCode, ticker, stockName, dividendPerShare, dividendDate);
            }
        } catch (Exception e) {
            log.warn("배당 정보 DB 저장 실패: stockCode={}, error={}", stockCode, e.getMessage());
        }
    }
    
    /**
     * 투자 비율 자동 계산
     * @param userId 사용자 ID
     * @param assetType 자산 유형
     * @param pricePerShare 주당 가격 (주식) 또는 null (외환)
     * @param quantity 수량
     * @param exchangeRate 환율 (외환의 경우 필수, 주식의 경우 주식 통화 -> 사용자 통화)
     * @param stockCode 종목코드 (주식의 경우 통화 판단용)
     * @return 투자 비율 (%)
     */
    private BigDecimal calculateInvestmentRatio(Long userId, AssetType assetType,
                                                BigDecimal pricePerShare, BigDecimal quantity,
                                                BigDecimal exchangeRate, String stockCode) {
        try {
            // 포트폴리오 설정에서 총 시드 조회
            PortfolioSettings settings = portfolioSettingsRepository.findByUserId(userId)
                .orElse(null);
            
            if (settings == null || settings.getTotalSeed() == null || 
                settings.getTotalSeed().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("포트폴리오 총 시드가 설정되지 않았습니다: userId={}", userId);
                return BigDecimal.ZERO;
            }
            
            BigDecimal totalSeed = settings.getTotalSeed();
            String userCurrency = settings.getCurrency() != null ? settings.getCurrency() : "KRW";
            
            // 총 투자 금액 계산
            BigDecimal totalInvestmentAmount;
            
            if (assetType == AssetType.STOCK) {
                // 주식: 평단가 × 수량
                if (pricePerShare == null || quantity == null) {
                    log.warn("주식 투자 비율 계산 실패: pricePerShare 또는 quantity가 null");
                    return BigDecimal.ZERO;
                }
                
                // 주식 통화 판단
                String stockCurrency = getCurrencyFromStockCode(stockCode);
                
                // 주식 통화로 총 투자 금액 계산
                BigDecimal totalInStockCurrency = pricePerShare.multiply(quantity);
                
                // 사용자 통화로 변환 (주식 통화 != 사용자 통화인 경우)
                if (!stockCurrency.equals(userCurrency)) {
                    BigDecimal exchangeRateToUser = getExchangeRate(stockCurrency, userCurrency, LocalDate.now());
                    totalInvestmentAmount = totalInStockCurrency.multiply(exchangeRateToUser);
                } else {
                    totalInvestmentAmount = totalInStockCurrency;
                }
                
            } else {
                // 외환: 수량 × 환율
                if (quantity == null || exchangeRate == null) {
                    log.warn("외환 투자 비율 계산 실패: quantity 또는 exchangeRate가 null");
                    return BigDecimal.ZERO;
                }
                
                // 외환의 경우 exchangeRate는 기준통화 -> 상대통화 환율
                // 총 투자 금액은 수량 × 환율 (원화 기준)
                totalInvestmentAmount = exchangeRate.multiply(quantity);
            }
            
            // 투자 비율 계산: (총 투자 금액 / 총 시드) × 100
            if (totalSeed.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = totalInvestmentAmount
                    .divide(totalSeed, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                
                // 투자 비율은 0.01% ~ 100% 범위로 제한
                if (ratio.compareTo(BigDecimal.valueOf(0.01)) < 0) {
                    ratio = BigDecimal.valueOf(0.01);
                } else if (ratio.compareTo(BigDecimal.valueOf(100)) > 0) {
                    ratio = BigDecimal.valueOf(100);
                }
                
                log.debug("투자 비율 계산: totalInvestmentAmount={}, totalSeed={}, ratio={}%", 
                    totalInvestmentAmount, totalSeed, ratio);
                
                return ratio;
            }
            
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("투자 비율 계산 중 오류 발생: userId={}, assetType={}", userId, assetType, e);
            return BigDecimal.ZERO;
        }
    }
    
    /**
     * 종목코드로부터 통화 판단
     * @param stockCode 종목코드
     * @return 통화 코드 (KRW, USD 등)
     */
    private String getCurrencyFromStockCode(String stockCode) {
        if (stockCode == null || stockCode.isEmpty()) {
            return "KRW"; // 기본값: 한국 주식
        }
        
        // 미국 주식 (영문 대문자만)
        if (stockCode.matches("^[A-Z]+$")) {
            return "USD";
        }
        
        // 한국 주식 (숫자)
        if (stockCode.matches("^\\d+$")) {
            return "KRW";
        }
        
        // 기타는 기본값
        return "KRW";
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
     * 고배당 투자 기록 조회
     */
    public ApiResponse<List<InvestmentRecord>> getHighDividendInvestments(Long userId, BigDecimal minDividendRate) {
        log.info("사용자 {}의 배당률 {}% 이상 투자 기록 조회", userId, minDividendRate);
        
        List<InvestmentRecord> investments = investmentRecordRepository
            .findHighDividendRecords(userId, minDividendRate);
        
        return ApiResponse.success(investments, "고배당 투자 기록을 조회했습니다.");
    }
    
    /**
     * 실현 손익 계산 (현재 가격 기준) - 레거시 지원
     */
    private void calculateRealizedProfit(InvestmentRecord sellRecord, InvestmentRecord buyRecord) {
        if (sellRecord.getSellQuantity() != null && buyRecord.getPricePerShare() != null) {
            BigDecimal buyAmount = buyRecord.getPricePerShare()
                .multiply(sellRecord.getSellQuantity());
            
            BigDecimal sellAmount = buyRecord.getCurrentPrice() != null ? 
                buyRecord.getCurrentPrice().multiply(sellRecord.getSellQuantity()) :
                buyAmount;
            
            BigDecimal profitAmount = sellAmount.subtract(buyAmount);
            BigDecimal profitRate = buyAmount.compareTo(BigDecimal.ZERO) > 0 ?
                profitAmount.divide(buyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
            sellRecord.setRealizedProfitAmount(profitAmount);
            sellRecord.setRealizedProfitRate(profitRate);
        }
    }
    
    /**
     * 실현 손익 계산 (평단가 기준, 현재 가격 사용)
     */
    private void calculateRealizedProfitWithAvgPrice(InvestmentRecord sellRecord, InvestmentRecord buyRecord, BigDecimal avgBuyPrice) {
        if (sellRecord.getSellQuantity() != null && avgBuyPrice != null && avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal buyAmount = avgBuyPrice
                .multiply(sellRecord.getSellQuantity());
            
            BigDecimal sellAmount = buyRecord.getCurrentPrice() != null ? 
                buyRecord.getCurrentPrice().multiply(sellRecord.getSellQuantity()) :
                buyAmount;
            
            BigDecimal profitAmount = sellAmount.subtract(buyAmount);
            BigDecimal profitRate = buyAmount.compareTo(BigDecimal.ZERO) > 0 ?
                profitAmount.divide(buyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
            sellRecord.setRealizedProfitAmount(profitAmount);
            sellRecord.setRealizedProfitRate(profitRate);
        }
    }
    
    /**
     * 실현 손익 계산 (매도금액 기준, 종목 전체 평단가 대비)
     * @param sellRecord 매도 기록
     * @param avgBuyPrice 종목 전체 평균 매수 단가
     * @param sellPrice 매도 단가
     */
    private void calculateRealizedProfitFromSellPrice(InvestmentRecord sellRecord, BigDecimal avgBuyPrice, BigDecimal sellPrice) {
        if (sellRecord.getSellQuantity() != null && avgBuyPrice != null && avgBuyPrice.compareTo(BigDecimal.ZERO) > 0) {
            // 평단가 기준 매수 금액
            BigDecimal buyAmount = avgBuyPrice
                .multiply(sellRecord.getSellQuantity());
            
            // 매도금액 (매도 단가 × 매도 수량)
            BigDecimal sellAmount = sellPrice.multiply(sellRecord.getSellQuantity());
            
            // 실현 손익 = 매도금액 - 매수금액
            BigDecimal profitAmount = sellAmount.subtract(buyAmount);
            
            // 실현 손익률 = (실현 손익 / 매수금액) × 100
            BigDecimal profitRate = buyAmount.compareTo(BigDecimal.ZERO) > 0 ?
                profitAmount.divide(buyAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            
            log.info("매도 손익 계산: avgBuyPrice={}, sellPrice={}, sellQuantity={}, profitAmount={}, profitRate={}%",
                avgBuyPrice, sellPrice, sellRecord.getSellQuantity(), profitAmount, profitRate);
            
            sellRecord.setRealizedProfitAmount(profitAmount);
            sellRecord.setRealizedProfitRate(profitRate);
        }
    }
    
    /**
     * 특정 날짜 시점의 평균 매수 단가 계산
     * 매도일 이전의 매수/매도 기록만 고려하여 해당 시점의 보유 수량 기준 평단가 계산
     */
    private BigDecimal calculateAverageBuyPriceAtDate(Long userId, String stockName, String stockCode, LocalDate targetDate, Long excludeRecordId) {
        try {
            List<InvestmentRecord> allRecords = investmentRecordRepository.findActiveRecordsByUserId(userId);
            
            // 같은 종목의 매수 기록만 필터링 (매도일 이전 또는 같은 날짜이지만 ID가 작은 경우만)
            List<InvestmentRecord> buyRecords = allRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY)
                .filter(r -> stockName.equals(r.getStockName()))
                .filter(r -> (stockCode == null && r.getStockCode() == null) || 
                             (stockCode != null && stockCode.equals(r.getStockCode())))
                .filter(r -> {
                    // 매도일 이전이면 포함
                    if (r.getRecordDate().isBefore(targetDate)) return true;
                    // 같은 날짜인 경우, 현재 수정 중인 매도 기록의 ID와 비교
                    if (r.getRecordDate().isEqual(targetDate)) {
                        // excludeRecordId가 있으면 (수정 모드), ID가 작은 경우만 포함
                        if (excludeRecordId != null) {
                            return r.getId() < excludeRecordId;
                        }
                        // excludeRecordId가 없으면 (생성 모드), 같은 날짜의 매수 기록은 제외
                        return false;
                    }
                    return false;
                })
                .filter(r -> r.getQuantity() != null && r.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .filter(r -> r.getPricePerShare() != null && r.getPricePerShare().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> {
                    int dateCompare = a.getRecordDate().compareTo(b.getRecordDate());
                    if (dateCompare != 0) return dateCompare;
                    return Long.compare(a.getId(), b.getId()); // 같은 날짜면 ID로 정렬
                })
                .collect(java.util.stream.Collectors.toList());
            
            if (buyRecords.isEmpty()) {
                return null;
            }
            
            // 매도일 이전 또는 같은 날짜의 매도 기록도 가져와서 보유 수량 계산 (단, 현재 수정 중인 매도 기록은 제외)
            List<InvestmentRecord> sellRecords = allRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.SELL)
                .filter(r -> stockName.equals(r.getStockName()))
                .filter(r -> (stockCode == null && r.getStockCode() == null) || 
                             (stockCode != null && stockCode.equals(r.getStockCode())))
                .filter(r -> r.getRecordDate().isBefore(targetDate) || r.getRecordDate().isEqual(targetDate)) // 매도일 이전 또는 같은 날짜
                .filter(r -> excludeRecordId == null || !r.getId().equals(excludeRecordId)) // 현재 수정 중인 매도 기록 제외
                .filter(r -> r.getSellQuantity() != null && r.getSellQuantity().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> {
                    int dateCompare = a.getRecordDate().compareTo(b.getRecordDate());
                    if (dateCompare != 0) return dateCompare;
                    return Long.compare(a.getId(), b.getId());
                })
                .collect(java.util.stream.Collectors.toList());
            
            // 날짜순으로 정렬된 매수/매도 기록을 순회하며 보유 수량과 평단가 추적
            // 매도 후 추가 매수가 있으면 평단가가 변하므로, 매도 시점의 실제 보유 수량과 평단가를 계산해야 함
            BigDecimal totalBuyAmount = BigDecimal.ZERO;
            BigDecimal holdingQuantity = BigDecimal.ZERO;
            BigDecimal currentAvgPrice = BigDecimal.ZERO;
            
            // 매수 기록과 매도 기록을 날짜순으로 병합하여 처리
            List<InvestmentRecord> allTransactions = new java.util.ArrayList<>();
            allTransactions.addAll(buyRecords);
            allTransactions.addAll(sellRecords);
            allTransactions.sort((a, b) -> {
                int dateCompare = a.getRecordDate().compareTo(b.getRecordDate());
                if (dateCompare != 0) return dateCompare;
                // 같은 날짜면 매도가 먼저 (매도 후 매수 순서) - 회사 분할 등으로 인한 수량 조정을 위해
                if (a.getType() != b.getType()) {
                    return a.getType() == InvestmentRecord.InvestmentType.SELL ? -1 : 1;
                }
                return Long.compare(a.getId(), b.getId());
            });
            
            // 각 거래를 순회하며 보유 수량과 평단가를 동적으로 계산
            for (InvestmentRecord record : allTransactions) {
                if (record.getType() == InvestmentRecord.InvestmentType.BUY) {
                    // 매수: 보유 수량과 평단가 업데이트
                    BigDecimal qty = record.getQuantity();
                    BigDecimal price = record.getPricePerShare();
                    BigDecimal amount = price.multiply(qty);
                    
                    if (holdingQuantity.compareTo(BigDecimal.ZERO) == 0) {
                        // 첫 매수
                        totalBuyAmount = amount;
                        holdingQuantity = qty;
                        currentAvgPrice = price;
                    } else {
                        // 추가 매수: 평단가 재계산
                        totalBuyAmount = totalBuyAmount.add(amount);
                        holdingQuantity = holdingQuantity.add(qty);
                        currentAvgPrice = totalBuyAmount.divide(holdingQuantity, 3, RoundingMode.HALF_UP);
                    }
                } else if (record.getType() == InvestmentRecord.InvestmentType.SELL) {
                    // 매도: 보유 수량만 감소, 평단가는 유지
                    BigDecimal sellQty = record.getSellQuantity();
                    holdingQuantity = holdingQuantity.subtract(sellQty);
                    if (holdingQuantity.compareTo(BigDecimal.ZERO) < 0) holdingQuantity = BigDecimal.ZERO;
                    // 총 매수 금액도 비례하여 조정 (평단가 유지)
                    if (holdingQuantity.compareTo(BigDecimal.ZERO) == 0) {
                        totalBuyAmount = BigDecimal.ZERO;
                        currentAvgPrice = BigDecimal.ZERO;
                    } else {
                        totalBuyAmount = currentAvgPrice.multiply(holdingQuantity);
                    }
                }
            }
            
            if (holdingQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            
            // 매도 시점의 평단가
            BigDecimal avgPrice = currentAvgPrice;
            
            log.info("매도 시점 평단가 계산: stockName={}, stockCode={}, targetDate={}, holdingQty={}, totalBuyAmount={}, avgPrice={}", 
                stockName, stockCode, targetDate, holdingQuantity, totalBuyAmount, avgPrice);
            
            return avgPrice;
        } catch (Exception e) {
            log.error("매도 시점 평단가 계산 중 오류 발생: userId={}, stockName={}, targetDate={}", userId, stockName, targetDate, e);
            return null;
        }
    }
    
    /**
     * 같은 종목의 전체 매수 기록에서 평균 매수 단가 계산
     * 평단가 = 총 매수 금액 / 총 매수 수량
     */
    private BigDecimal calculateAverageBuyPrice(Long userId, String stockName, String stockCode) {
        try {
            List<InvestmentRecord> allRecords = investmentRecordRepository.findActiveRecordsByUserId(userId);
            
            // 같은 종목의 매수 기록만 필터링
            List<InvestmentRecord> buyRecords = allRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY)
                .filter(r -> stockName.equals(r.getStockName()))
                .filter(r -> (stockCode == null && r.getStockCode() == null) || 
                             (stockCode != null && stockCode.equals(r.getStockCode())))
                .filter(r -> r.getQuantity() != null && r.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .filter(r -> r.getPricePerShare() != null && r.getPricePerShare().compareTo(BigDecimal.ZERO) > 0)
                .collect(java.util.stream.Collectors.toList());
            
            if (buyRecords.isEmpty()) {
                return null;
            }
            
            // 총 매수 금액 = 각 매수 기록의 (단가 × 수량) 합계
            BigDecimal totalBuyAmount = BigDecimal.ZERO;
            BigDecimal totalQuantity = BigDecimal.ZERO;
            
            for (InvestmentRecord buyRecord : buyRecords) {
                BigDecimal amount = buyRecord.getPricePerShare()
                    .multiply(buyRecord.getQuantity());
                totalBuyAmount = totalBuyAmount.add(amount);
                totalQuantity = totalQuantity.add(buyRecord.getQuantity());
            }
            
            if (totalQuantity.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            
            // 평단가 = 총 매수 금액 / 총 매수 수량 (소수점 셋째자리에서 반올림)
            BigDecimal avgPrice = totalBuyAmount.divide(totalQuantity, 3, RoundingMode.HALF_UP);
            
            log.info("종목 평균 매수가 계산: stockName={}, stockCode={}, records={}, totalAmount={}, totalQty={}, avgPrice={}", 
                stockName, stockCode, buyRecords.size(), totalBuyAmount, totalQuantity, avgPrice);
            
            return avgPrice;
        } catch (Exception e) {
            log.error("평균 매수가 계산 중 오류 발생: userId={}, stockName={}", userId, stockName, e);
            return null;
        }
    }
    
    /**
     * 같은 종목의 전체 보유 수량 계산 (모든 매수 기록의 합 - 모든 매도 기록의 합)
     */
    private BigDecimal calculateStockHoldingQuantity(Long userId, String stockName, String stockCode) {
        try {
            List<InvestmentRecord> allRecords = investmentRecordRepository.findActiveRecordsByUserId(userId);
            
            // 같은 종목의 매수 수량 합계
            BigDecimal totalBuyQuantity = allRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.BUY)
                .filter(r -> stockName.equals(r.getStockName()))
                .filter(r -> (stockCode == null && r.getStockCode() == null) || 
                             (stockCode != null && stockCode.equals(r.getStockCode())))
                .filter(r -> r.getQuantity() != null)
                .map(InvestmentRecord::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // 같은 종목의 매도 수량 합계
            BigDecimal totalSellQuantity = allRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.SELL)
                .filter(r -> stockName.equals(r.getStockName()))
                .filter(r -> (stockCode == null && r.getStockCode() == null) || 
                             (stockCode != null && stockCode.equals(r.getStockCode())))
                .filter(r -> r.getSellQuantity() != null)
                .map(InvestmentRecord::getSellQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal holdingQuantity = totalBuyQuantity.subtract(totalSellQuantity);
            
            log.info("종목 보유 수량 계산: stockName={}, buyQty={}, sellQty={}, holdingQty={}", 
                stockName, totalBuyQuantity, totalSellQuantity, holdingQuantity);
            
            return holdingQuantity.compareTo(BigDecimal.ZERO) > 0 ? holdingQuantity : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("종목 보유 수량 계산 중 오류 발생: userId={}, stockName={}", userId, stockName, e);
            return null;
        }
    }
    
    /**
     * 선택된 종목의 보유 수량 계산
     * @param userId 사용자 ID
     * @param buyRecordId 매수 기록 ID
     * @return 보유 수량 (매수 수량 - 매도 수량)
     */
    private BigDecimal calculateHoldingQuantity(Long userId, Long buyRecordId) {
        try {
            // 매수 기록 조회
            InvestmentRecord buyRecord = investmentRecordRepository.findById(buyRecordId)
                .orElse(null);
            
            if (buyRecord == null || !buyRecord.getUser().getId().equals(userId)) {
                return null;
            }
            
            // 매수 수량
            BigDecimal buyQuantity = buyRecord.getQuantity();
            if (buyQuantity == null) {
                return null;
            }
            
            // 해당 종목의 매도 수량 합계
            List<InvestmentRecord> allUserRecords = investmentRecordRepository
                .findActiveRecordsByUserId(userId);
            
            BigDecimal totalSellQuantity = allUserRecords.stream()
                .filter(r -> r.getType() == InvestmentRecord.InvestmentType.SELL)
                .filter(r -> r.getSelectedStockId() != null && r.getSelectedStockId().equals(buyRecordId))
                .filter(r -> r.getSellQuantity() != null)
                .map(InvestmentRecord::getSellQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // 보유 수량 = 매수 수량 - 매도 수량
            BigDecimal holdingQuantity = buyQuantity.subtract(totalSellQuantity);
            
            return holdingQuantity.compareTo(BigDecimal.ZERO) > 0 ? holdingQuantity : BigDecimal.ZERO;
            
        } catch (Exception e) {
            log.error("보유 수량 계산 중 오류 발생: userId={}, buyRecordId={}", userId, buyRecordId, e);
            return null;
        }
    }
    
    /**
     * 투자 통계 조회
     */
    public ApiResponse<Map<String, Object>> getInvestmentStats(Long userId) {
        log.info("사용자 {}의 투자 통계 조회", userId);
        
        long totalRecords = investmentRecordRepository.countActiveRecordsByUserId(userId);
        long buyRecords = investmentRecordRepository.countRecordsByType(userId, InvestmentRecord.InvestmentType.BUY);
        long sellRecords = investmentRecordRepository.countRecordsByType(userId, InvestmentRecord.InvestmentType.SELL);
        
        Map<String, Object> stats = Map.of(
            "totalRecords", totalRecords,
            "buyRecords", buyRecords,
            "sellRecords", sellRecords
        );
        
        return ApiResponse.success(stats, "투자 통계를 조회했습니다.");
    }
} 