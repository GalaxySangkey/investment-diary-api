package com.investmentdiary.controller;

import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.security.JwtTokenProvider;
import com.investmentdiary.service.StockDataBatchService;
import com.investmentdiary.service.StockListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 주식 데이터 배치 작업을 수동으로 트리거하는 컨트롤러
 * 관리자 또는 개발자가 테스트/수동 실행 시 사용
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stock-data")
@Tag(name = "주식 데이터", description = "주식 종가, 배당, 환율 데이터 관리 API")
public class StockDataController {
    
    private final StockDataBatchService stockDataBatchService;
    private final StockListingService stockListingService;
    private final JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    public StockDataController(
            StockDataBatchService stockDataBatchService,
            StockListingService stockListingService,
            JwtTokenProvider jwtTokenProvider) {
        this.stockDataBatchService = stockDataBatchService;
        this.stockListingService = stockListingService;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * 요청에서 사용자 ID 추출
     */
    private Long getUserIdFromRequest(HttpServletRequest request) {
        // 쿠키에서 accessToken 추출
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (jwtTokenProvider.validateToken(token)) {
                        return jwtTokenProvider.getUserIdFromToken(token);
                    }
                }
            }
        }
        throw new RuntimeException("JWT 토큰을 찾을 수 없습니다.");
    }
    
    /**
     * 일일 주식 종가 업데이트 수동 실행
     * 관리자 권한 필요 (임시로 인증된 사용자 모두 허용)
     */
    @PostMapping("/update-prices")
    @Operation(summary = "주식 종가 업데이트", description = "회원들이 보유 중인 모든 종목의 종가를 조회하여 DB에 저장합니다.")
    @PreAuthorize("isAuthenticated()") // 임시로 인증된 사용자 모두 허용
    public ApiResponse<Map<String, Object>> updateStockPrices() {
        log.info("수동 종가 업데이트 요청");
        try {
            stockDataBatchService.updateDailyStockPrices();
            return ApiResponse.success(
                Map.of("message", "종가 업데이트가 시작되었습니다."),
                "종가 업데이트가 성공적으로 시작되었습니다."
            );
        } catch (Exception e) {
            log.error("종가 업데이트 실패", e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, "종가 업데이트 실패: " + e.getMessage());
        }
    }
    
    /**
     * 월별 배당 정보 업데이트 수동 실행
     * 관리자 권한 필요 (임시로 인증된 사용자 모두 허용)
     */
    @PostMapping("/update-dividends")
    @Operation(summary = "배당 정보 업데이트", description = "회원들이 보유 중인 모든 종목의 배당 정보를 조회하여 DB에 저장합니다.")
    @PreAuthorize("isAuthenticated()") // 임시로 인증된 사용자 모두 허용
    public ApiResponse<Map<String, Object>> updateDividends() {
        log.info("수동 배당 정보 업데이트 요청");
        try {
            stockDataBatchService.updateMonthlyDividends();
            return ApiResponse.success(
                Map.of("message", "배당 정보 업데이트가 시작되었습니다."),
                "배당 정보 업데이트가 성공적으로 시작되었습니다."
            );
        } catch (Exception e) {
            log.error("배당 정보 업데이트 실패", e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, "배당 정보 업데이트 실패: " + e.getMessage());
        }
    }
    
    /**
     * 일일 환율 업데이트 수동 실행
     * 관리자 권한 필요 (임시로 인증된 사용자 모두 허용)
     */
    @PostMapping("/update-exchange-rates")
    @Operation(summary = "환율 업데이트", description = "주요 통화쌍(USD/KRW, EUR/KRW 등)의 환율을 조회하여 DB에 저장합니다.")
    @PreAuthorize("isAuthenticated()") // 임시로 인증된 사용자 모두 허용
    public ApiResponse<Map<String, Object>> updateExchangeRates() {
        log.info("수동 환율 업데이트 요청");
        try {
            stockDataBatchService.updateDailyExchangeRates();
            return ApiResponse.success(
                Map.of("message", "환율 업데이트가 시작되었습니다."),
                "환율 업데이트가 성공적으로 시작되었습니다."
            );
        } catch (Exception e) {
            log.error("환율 업데이트 실패", e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, "환율 업데이트 실패: " + e.getMessage());
        }
    }
    
    /**
     * 특정 종목의 과거 종가 데이터 채우기
     */
    @PostMapping("/fill-history/{stockCode}")
    @Operation(summary = "과거 종가 데이터 채우기", description = "특정 종목의 과거 종가 데이터를 일괄 조회하여 DB에 저장합니다.")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> fillHistoricalPrices(
            @Parameter(description = "종목코드 (예: 000390, AAPL)")
            @PathVariable("stockCode") String stockCode,
            @Parameter(description = "시작 날짜 (YYYY-MM-DD)")
            @RequestParam(value = "startDate") String startDate,
            @Parameter(description = "종료 날짜 (YYYY-MM-DD, 기본값: 오늘)")
            @RequestParam(value = "endDate", required = false) String endDate) {
        
        log.info("과거 종가 데이터 채우기 요청: stockCode={}, startDate={}, endDate={}", 
            stockCode, startDate, endDate);
        
        try {
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();
            
            int savedCount = stockDataBatchService.fillHistoricalPrices(stockCode, start, end);
            
            return ApiResponse.success(
                Map.of(
                    "stockCode", stockCode,
                    "startDate", startDate,
                    "endDate", end.toString(),
                    "savedCount", savedCount,
                    "message", String.format("과거 종가 %d건이 저장되었습니다.", savedCount)
                ),
                "과거 종가 데이터 채우기가 완료되었습니다."
            );
        } catch (Exception e) {
            log.error("과거 종가 데이터 채우기 실패", e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, 
                "과거 종가 데이터 채우기 실패: " + e.getMessage());
        }
    }
    
    /**
     * 종목 검색 API (DB에서 조회)
     * 인증 불필요 - 종목 검색은 누구나 사용 가능
     */
    @GetMapping("/stocks/search")
    @Operation(summary = "종목 검색", description = "종목명 또는 종목코드로 종목을 검색합니다. DB 기반으로 KRX 서버 장애와 무관하게 동작합니다.")
    public ApiResponse<Map<String, Object>> searchStocks(
            @Parameter(description = "검색어 (종목명 또는 종목코드)", required = true)
            @RequestParam(value = "query", required = true) String query,
            @Parameter(description = "시장 필터 (KR/US/JP/EU/ALL, 기본 ALL)")
            @RequestParam(value = "market", defaultValue = "ALL") String market,
            @Parameter(description = "결과 제한 수 (기본 20)")
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        
        if (query == null || query.trim().isEmpty()) {
            return ApiResponse.error(ResponseCode.BAD_REQUEST, "검색어를 입력해주세요.");
        }

        try {
            List<Map<String, Object>> results = new ArrayList<>();
            
            // 유럽 주식 검색 및 DB 저장
            if ("EU".equalsIgnoreCase(market)) {
                // 먼저 DB에서 검색
                List<Map<String, Object>> dbResults = stockListingService.searchStocks(query.trim(), "EU", limit);
                
                if (!dbResults.isEmpty()) {
                    // DB에 결과가 있으면 그대로 사용
                    results = dbResults;
                } else {
                    // DB에 없으면 외부 API 검색 후 DB에 저장
                    List<Map<String, Object>> euResults = stockListingService.searchStocksFromPython(query.trim(), "EU", limit);
                    if (!euResults.isEmpty()) {
                        // 검색 결과를 DB에 저장 (비동기로 처리하여 응답 속도에 영향 없도록)
                        new Thread(() -> {
                            try {
                                stockListingService.saveEuStockListings(euResults);
                            } catch (Exception e) {
                                log.warn("유럽 종목 DB 저장 실패 (무시): {}", e.getMessage());
                            }
                        }, "eu-stock-save").start();
                    }
                    results = euResults;
                }
            } else if ("ALL".equalsIgnoreCase(market)) {
                // 전체 검색: DB에서 KR/US/JP 검색 + 유럽 검색
                List<Map<String, Object>> dbResults = stockListingService.searchStocks(query.trim(), null, limit);
                
                // 유럽 종목 검색 (DB 먼저, 없으면 외부 API)
                List<Map<String, Object>> euResults;
                List<Map<String, Object>> dbEuResults = stockListingService.searchStocks(query.trim(), "EU", limit);
                if (!dbEuResults.isEmpty()) {
                    euResults = dbEuResults;
                } else {
                    euResults = stockListingService.searchStocksFromPython(query.trim(), "EU", limit);
                    // 검색 결과를 DB에 저장 (비동기)
                    if (!euResults.isEmpty()) {
                        final List<Map<String, Object>> finalEuResults = euResults;
                        new Thread(() -> {
                            try {
                                stockListingService.saveEuStockListings(finalEuResults);
                            } catch (Exception e) {
                                log.warn("유럽 종목 DB 저장 실패 (무시): {}", e.getMessage());
                            }
                        }, "eu-stock-save").start();
                    }
                }
                
                // 결과 합치기 (중복 제거는 stockCode + country 기준)
                Set<String> seenKeys = new HashSet<>();
                for (Map<String, Object> item : dbResults) {
                    String key = item.get("stockCode") + "|" + item.get("country");
                    if (!seenKeys.contains(key)) {
                        results.add(item);
                        seenKeys.add(key);
                    }
                }
                for (Map<String, Object> item : euResults) {
                    String key = item.get("stockCode") + "|" + item.get("country");
                    if (!seenKeys.contains(key) && results.size() < limit) {
                        results.add(item);
                        seenKeys.add(key);
                    }
                }
            } else {
                // 특정 국가만 검색 (KR/US/JP)
                String country = "KR".equalsIgnoreCase(market) ? "KR" : 
                                 "US".equalsIgnoreCase(market) ? "US" : 
                                 "JP".equalsIgnoreCase(market) ? "JP" : null;
                
                results = stockListingService.searchStocks(query.trim(), country, limit);
            }
            
            return ApiResponse.success(
                Map.of(
                    "query", query,
                    "market", market,
                    "count", results.size(),
                    "results", results
                ),
                "종목 검색 완료"
            );
        } catch (Exception e) {
            log.error("종목 검색 실패: query={}", query, e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, "종목 검색 실패: " + e.getMessage());
        }
    }

    /**
     * 종목 리스트 수동 동기화 (관리자용)
     */
    @PostMapping("/stocks/sync")
    @Operation(summary = "종목 리스트 동기화", description = "외부 데이터 소스에서 최신 종목 리스트를 가져와 DB에 동기화합니다.")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> syncStockListings(
            @Parameter(description = "국가 (KR/US/JP, 기본 KR)")
            @RequestParam(defaultValue = "KR") String country) {
        log.info("수동 종목 리스트 동기화 요청: country={}", country);
        try {
            int count = stockListingService.syncListingsFromPython(country.toUpperCase());
            return ApiResponse.success(
                Map.of(
                    "country", country,
                    "syncedCount", count,
                    "message", String.format("%s 종목 %d건이 동기화되었습니다.", country, count)
                ),
                "종목 리스트 동기화가 완료되었습니다."
            );
        } catch (Exception e) {
            log.error("종목 리스트 동기화 실패", e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, "동기화 실패: " + e.getMessage());
        }
    }

    /**
     * 현재 로그인한 사용자의 모든 보유 종목에 대해 과거 종가 데이터 채우기
     */
    @PostMapping("/fill-all-history")
    @Operation(summary = "모든 보유 종목 과거 종가 채우기", 
        description = "현재 사용자의 모든 보유 종목에 대해 첫 매수일부터 오늘까지의 종가 데이터를 채웁니다.")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Map<String, Object>> fillAllHistoricalPrices(HttpServletRequest request) {
        
        Long userId = getUserIdFromRequest(request);
        log.info("모든 보유 종목 과거 종가 채우기 요청: userId={}", userId);
        
        try {
            int totalSaved = stockDataBatchService.fillAllHistoricalPricesForUser(userId);
            
            return ApiResponse.success(
                Map.of(
                    "userId", userId,
                    "totalSavedCount", totalSaved,
                    "message", String.format("총 %d건의 과거 종가가 저장되었습니다.", totalSaved)
                ),
                "모든 보유 종목의 과거 종가 데이터 채우기가 완료되었습니다."
            );
        } catch (Exception e) {
            log.error("모든 보유 종목 과거 종가 채우기 실패", e);
            return ApiResponse.error(ResponseCode.INTERNAL_SERVER_ERROR, 
                "과거 종가 데이터 채우기 실패: " + e.getMessage());
        }
    }
}

