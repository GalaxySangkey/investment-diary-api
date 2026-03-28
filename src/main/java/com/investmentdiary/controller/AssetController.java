package com.investmentdiary.controller;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.entity.AssetSettings;
import com.investmentdiary.entity.FixedExpense;
import com.investmentdiary.entity.FixedIncome;
import com.investmentdiary.entity.MonthlyActualBalance;
import com.investmentdiary.service.AssetService;
import com.investmentdiary.security.JwtTokenProvider;
import com.investmentdiary.util.ResponseConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/asset")
@Tag(name = "자산관리", description = "자산관리 API")
public class AssetController {
    
    private static final Logger log = LoggerFactory.getLogger(AssetController.class);
    
    private final AssetService assetService;
    private final JwtTokenProvider jwtTokenProvider;
    
    public AssetController(AssetService assetService, JwtTokenProvider jwtTokenProvider) {
        this.assetService = assetService;
        this.jwtTokenProvider = jwtTokenProvider;
    }
    
    /**
     * 요청에서 JWT 토큰 추출
     * 우선순위: 1. 쿠키 2. Authorization 헤더 (하위 호환성)
     */
    private String getJwtFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        // 1. 쿠키에서 토큰 읽기
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    String token = cookie.getValue();
                    if (StringUtils.hasText(token)) {
                        return token;
                    }
                }
            }
        }
        
        // 2. Authorization 헤더에서 토큰 읽기 (하위 호환성)
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    /**
     * 요청에서 사용자 ID 추출
     */
    private Long getUserIdFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);
        if (jwt != null) {
            return jwtTokenProvider.getUserIdFromToken(jwt);
        }
        throw new IllegalArgumentException("JWT 토큰을 찾을 수 없습니다.");
    }
    
    @GetMapping("/settings")
    @Operation(summary = "자산 설정 조회", description = "사용자의 자산 설정(시작 날짜, 시작 금액)을 조회합니다.")
    public UnifiedApiResponse<AssetSettings> getAssetSettings(
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<AssetSettings> response = assetService.getAssetSettings(userId);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PostMapping("/settings")
    @Operation(summary = "자산 설정 저장", description = "사용자의 자산 설정(시작 날짜, 시작 금액, 월 저축액, 기존 저축액, 퇴직연금, 투자시드)을 저장합니다.")
    public UnifiedApiResponse<AssetSettings> saveAssetSettings(
            @Parameter(description = "시작 날짜 (yyyy-MM-dd)")
            @RequestParam(value = "startDate") @NotNull String startDateStr,
            @Parameter(description = "시작 금액")
            @RequestParam(value = "initialBalance") @NotNull String initialBalanceStr,
            @Parameter(description = "월 저축액 (선택)")
            @RequestParam(value = "savings", required = false) String savingsStr,
            @Parameter(description = "기존 저축액 (선택)")
            @RequestParam(value = "existingSavings", required = false) String existingSavingsStr,
            @Parameter(description = "퇴직연금 (선택)")
            @RequestParam(value = "retirementPension", required = false) String retirementPensionStr,
            @Parameter(description = "투자시드 (선택)")
            @RequestParam(value = "investmentSeed", required = false) String investmentSeedStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("자산 설정 저장 요청: startDateStr={}, initialBalanceStr={}, savingsStr={}, existingSavingsStr={}, retirementPensionStr={}, investmentSeedStr={}", startDateStr, initialBalanceStr, savingsStr, existingSavingsStr, retirementPensionStr, investmentSeedStr);
        
        // String을 LocalDate로 파싱
        LocalDate startDate;
        try {
            startDate = LocalDate.parse(startDateStr);
        } catch (Exception e) {
            log.error("날짜 파싱 실패: {}", startDateStr, e);
            throw new IllegalArgumentException("잘못된 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + startDateStr);
        }
        
        // String을 BigDecimal로 파싱
        BigDecimal initialBalance;
        try {
            initialBalance = new BigDecimal(initialBalanceStr);
        } catch (NumberFormatException e) {
            log.error("금액 파싱 실패: {}", initialBalanceStr, e);
            throw new IllegalArgumentException("잘못된 금액 형식입니다: " + initialBalanceStr);
        }
        
        BigDecimal savings = null;
        if (savingsStr != null && !savingsStr.trim().isEmpty()) {
            try {
                savings = new BigDecimal(savingsStr);
            } catch (NumberFormatException e) {
                log.error("월 저축액 파싱 실패: {}", savingsStr, e);
                throw new IllegalArgumentException("잘못된 월 저축액 형식입니다: " + savingsStr);
            }
        }
        
        BigDecimal existingSavings = null;
        if (existingSavingsStr != null && !existingSavingsStr.trim().isEmpty()) {
            try {
                existingSavings = new BigDecimal(existingSavingsStr);
            } catch (NumberFormatException e) {
                log.error("기존 저축액 파싱 실패: {}", existingSavingsStr, e);
                throw new IllegalArgumentException("잘못된 기존 저축액 형식입니다: " + existingSavingsStr);
            }
        }
        
        BigDecimal retirementPension = null;
        if (retirementPensionStr != null && !retirementPensionStr.trim().isEmpty()) {
            try {
                retirementPension = new BigDecimal(retirementPensionStr);
            } catch (NumberFormatException e) {
                log.error("퇴직연금 파싱 실패: {}", retirementPensionStr, e);
                throw new IllegalArgumentException("잘못된 퇴직연금 형식입니다: " + retirementPensionStr);
            }
        }
        
        BigDecimal investmentSeed = null;
        if (investmentSeedStr != null && !investmentSeedStr.trim().isEmpty()) {
            try {
                investmentSeed = new BigDecimal(investmentSeedStr);
            } catch (NumberFormatException e) {
                log.error("투자시드 파싱 실패: {}", investmentSeedStr, e);
                throw new IllegalArgumentException("잘못된 투자시드 형식입니다: " + investmentSeedStr);
            }
        }
        
        log.info("파싱된 날짜: {}, 파싱된 금액: {}, 파싱된 저축액: {}, 파싱된 기존 저축액: {}, 파싱된 퇴직연금: {}, 파싱된 투자시드: {}", startDate, initialBalance, savings, existingSavings, retirementPension, investmentSeed);
        
        Long userId = getUserIdFromRequest(httpRequest);
        log.info("사용자 ID: {}", userId);
        
        ApiResponse<AssetSettings> response = assetService.saveAssetSettings(userId, startDate, initialBalance, savings, existingSavings, retirementPension, investmentSeed);
        
        log.info("자산 설정 저장 완료: {}", response.getData());
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/fixed-incomes")
    @Operation(summary = "고정 수입 목록 조회", description = "사용자의 고정 수입 목록을 조회합니다.")
    public UnifiedApiResponse<List<FixedIncome>> getFixedIncomes(
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<List<FixedIncome>> response = assetService.getFixedIncomes(userId);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PostMapping("/fixed-incomes")
    @Operation(summary = "고정 수입 생성", description = "새로운 고정 수입을 생성합니다.")
    public UnifiedApiResponse<FixedIncome> createFixedIncome(
            @Parameter(description = "수입 항목명")
            @RequestParam(value = "name") @NotNull String name,
            @Parameter(description = "금액")
            @RequestParam(value = "amount") @NotNull String amountStr,
            @Parameter(description = "일자 (1-31)")
            @RequestParam(value = "day") @NotNull String dayStr,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "endDate", required = false) String endDateStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("고정 수입 생성 요청: name={}, amountStr={}, dayStr={}, startDateStr={}, endDateStr={}", name, amountStr, dayStr, startDateStr, endDateStr);
        
        BigDecimal amount;
        Integer day;
        try {
            amount = new BigDecimal(amountStr);
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            log.error("파싱 실패: amountStr={}, dayStr={}", amountStr, dayStr, e);
            throw new IllegalArgumentException("잘못된 금액 또는 일자 형식입니다.");
        }
        
        LocalDate startDate = null;
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                startDate = LocalDate.parse(startDateStr);
            } catch (Exception e) {
                log.error("시작 날짜 파싱 실패: {}", startDateStr, e);
                throw new IllegalArgumentException("잘못된 시작 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + startDateStr);
            }
        }
        
        LocalDate endDate = null;
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(endDateStr);
            } catch (Exception e) {
                log.error("종료 날짜 파싱 실패: {}", endDateStr, e);
                throw new IllegalArgumentException("잘못된 종료 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + endDateStr);
            }
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<FixedIncome> response = assetService.createFixedIncome(userId, name, amount, day, startDate, endDate);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PutMapping("/fixed-incomes/{id}")
    @Operation(summary = "고정 수입 수정", description = "고정 수입을 수정합니다.")
    public UnifiedApiResponse<FixedIncome> updateFixedIncome(
            @Parameter(description = "고정 수입 ID")
            @PathVariable(value = "id") @NotNull String idStr,
            @Parameter(description = "수입 항목명")
            @RequestParam(value = "name") @NotNull String name,
            @Parameter(description = "금액")
            @RequestParam(value = "amount") @NotNull String amountStr,
            @Parameter(description = "일자 (1-31)")
            @RequestParam(value = "day") @NotNull String dayStr,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "endDate", required = false) String endDateStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("고정 수입 수정 요청: idStr={}, name={}, amountStr={}, dayStr={}, startDateStr={}, endDateStr={}", idStr, name, amountStr, dayStr, startDateStr, endDateStr);
        
        Long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.error("ID 파싱 실패: {}", idStr, e);
            throw new IllegalArgumentException("잘못된 ID 형식입니다: " + idStr);
        }
        
        BigDecimal amount;
        Integer day;
        try {
            amount = new BigDecimal(amountStr);
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            log.error("파싱 실패: amountStr={}, dayStr={}", amountStr, dayStr, e);
            throw new IllegalArgumentException("잘못된 금액 또는 일자 형식입니다.");
        }
        
        LocalDate startDate = null;
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                startDate = LocalDate.parse(startDateStr);
            } catch (Exception e) {
                log.error("시작 날짜 파싱 실패: {}", startDateStr, e);
                throw new IllegalArgumentException("잘못된 시작 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + startDateStr);
            }
        }
        
        LocalDate endDate = null;
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(endDateStr);
            } catch (Exception e) {
                log.error("종료 날짜 파싱 실패: {}", endDateStr, e);
                throw new IllegalArgumentException("잘못된 종료 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + endDateStr);
            }
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<FixedIncome> response = assetService.updateFixedIncome(userId, id, name, amount, day, startDate, endDate);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @DeleteMapping("/fixed-incomes/{id}")
    @Operation(summary = "고정 수입 삭제", description = "고정 수입을 삭제합니다.")
    public UnifiedApiResponse<Void> deleteFixedIncome(
            @Parameter(description = "고정 수입 ID")
            @PathVariable(value = "id") @NotNull String idStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("고정 수입 삭제 요청: idStr={}", idStr);
        
        Long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.error("ID 파싱 실패: {}", idStr, e);
            throw new IllegalArgumentException("잘못된 ID 형식입니다: " + idStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<Void> response = assetService.deleteFixedIncome(userId, id);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/fixed-expenses")
    @Operation(summary = "고정 지출 목록 조회", description = "사용자의 고정 지출 목록을 조회합니다.")
    public UnifiedApiResponse<List<FixedExpense>> getFixedExpenses(
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<List<FixedExpense>> response = assetService.getFixedExpenses(userId);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PostMapping("/fixed-expenses")
    @Operation(summary = "고정 지출 생성", description = "새로운 고정 지출을 생성합니다.")
    public UnifiedApiResponse<FixedExpense> createFixedExpense(
            @Parameter(description = "지출 항목명")
            @RequestParam(value = "name") @NotNull String name,
            @Parameter(description = "금액")
            @RequestParam(value = "amount") @NotNull String amountStr,
            @Parameter(description = "일자 (1-31)")
            @RequestParam(value = "day") @NotNull String dayStr,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "endDate", required = false) String endDateStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("고정 지출 생성 요청: name={}, amountStr={}, dayStr={}, startDateStr={}, endDateStr={}", name, amountStr, dayStr, startDateStr, endDateStr);
        
        BigDecimal amount;
        Integer day;
        try {
            amount = new BigDecimal(amountStr);
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            log.error("파싱 실패: amountStr={}, dayStr={}", amountStr, dayStr, e);
            throw new IllegalArgumentException("잘못된 금액 또는 일자 형식입니다.");
        }
        
        LocalDate startDate = null;
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                startDate = LocalDate.parse(startDateStr);
            } catch (Exception e) {
                log.error("시작 날짜 파싱 실패: {}", startDateStr, e);
                throw new IllegalArgumentException("잘못된 시작 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + startDateStr);
            }
        }
        
        LocalDate endDate = null;
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(endDateStr);
            } catch (Exception e) {
                log.error("종료 날짜 파싱 실패: {}", endDateStr, e);
                throw new IllegalArgumentException("잘못된 종료 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + endDateStr);
            }
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<FixedExpense> response = assetService.createFixedExpense(userId, name, amount, day, startDate, endDate);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PutMapping("/fixed-expenses/{id}")
    @Operation(summary = "고정 지출 수정", description = "고정 지출을 수정합니다.")
    public UnifiedApiResponse<FixedExpense> updateFixedExpense(
            @Parameter(description = "고정 지출 ID")
            @PathVariable(value = "id") @NotNull String idStr,
            @Parameter(description = "지출 항목명")
            @RequestParam(value = "name") @NotNull String name,
            @Parameter(description = "금액")
            @RequestParam(value = "amount") @NotNull String amountStr,
            @Parameter(description = "일자 (1-31)")
            @RequestParam(value = "day") @NotNull String dayStr,
            @Parameter(description = "시작 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @Parameter(description = "종료 날짜 (yyyy-MM-dd, 선택)")
            @RequestParam(value = "endDate", required = false) String endDateStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("고정 지출 수정 요청: idStr={}, name={}, amountStr={}, dayStr={}, startDateStr={}, endDateStr={}", idStr, name, amountStr, dayStr, startDateStr, endDateStr);
        
        Long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.error("ID 파싱 실패: {}", idStr, e);
            throw new IllegalArgumentException("잘못된 ID 형식입니다: " + idStr);
        }
        
        BigDecimal amount;
        Integer day;
        try {
            amount = new BigDecimal(amountStr);
            day = Integer.parseInt(dayStr);
        } catch (NumberFormatException e) {
            log.error("파싱 실패: amountStr={}, dayStr={}", amountStr, dayStr, e);
            throw new IllegalArgumentException("잘못된 금액 또는 일자 형식입니다.");
        }
        
        LocalDate startDate = null;
        if (startDateStr != null && !startDateStr.trim().isEmpty()) {
            try {
                startDate = LocalDate.parse(startDateStr);
            } catch (Exception e) {
                log.error("시작 날짜 파싱 실패: {}", startDateStr, e);
                throw new IllegalArgumentException("잘못된 시작 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + startDateStr);
            }
        }
        
        LocalDate endDate = null;
        if (endDateStr != null && !endDateStr.trim().isEmpty()) {
            try {
                endDate = LocalDate.parse(endDateStr);
            } catch (Exception e) {
                log.error("종료 날짜 파싱 실패: {}", endDateStr, e);
                throw new IllegalArgumentException("잘못된 종료 날짜 형식입니다. yyyy-MM-dd 형식으로 입력해주세요: " + endDateStr);
            }
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<FixedExpense> response = assetService.updateFixedExpense(userId, id, name, amount, day, startDate, endDate);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @DeleteMapping("/fixed-expenses/{id}")
    @Operation(summary = "고정 지출 삭제", description = "고정 지출을 삭제합니다.")
    public UnifiedApiResponse<Void> deleteFixedExpense(
            @Parameter(description = "고정 지출 ID")
            @PathVariable(value = "id") @NotNull String idStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("고정 지출 삭제 요청: idStr={}", idStr);
        
        Long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            log.error("ID 파싱 실패: {}", idStr, e);
            throw new IllegalArgumentException("잘못된 ID 형식입니다: " + idStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<Void> response = assetService.deleteFixedExpense(userId, id);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/monthly-balance/{year}/{month}")
    @Operation(summary = "월별 실제 금액 조회", description = "특정 연도/월의 실제 금액을 조회합니다.")
    public UnifiedApiResponse<MonthlyActualBalance> getMonthlyActualBalance(
            @Parameter(description = "연도")
            @PathVariable(value = "year") @NotNull String yearStr,
            @Parameter(description = "월 (1-12)")
            @PathVariable(value = "month") @NotNull String monthStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("월별 실제 금액 조회 요청: year={}, month={}", yearStr, monthStr);
        
        // String을 Integer로 파싱
        Integer year;
        Integer month;
        try {
            year = Integer.parseInt(yearStr);
            month = Integer.parseInt(monthStr);
        } catch (NumberFormatException e) {
            log.error("숫자 파싱 실패: year={}, month={}", yearStr, monthStr, e);
            throw new IllegalArgumentException("잘못된 연도 또는 월 형식입니다: year=" + yearStr + ", month=" + monthStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<MonthlyActualBalance> response = assetService.getMonthlyActualBalance(userId, year, month);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/monthly-balance/{year}")
    @Operation(summary = "연도별 월별 실제 금액 목록 조회", description = "특정 연도의 모든 월별 실제 금액을 조회합니다.")
    public UnifiedApiResponse<List<MonthlyActualBalance>> getMonthlyActualBalancesByYear(
            @Parameter(description = "연도")
            @PathVariable(value = "year") @NotNull String yearStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("연도별 월별 실제 금액 목록 조회 요청: year={}", yearStr);
        
        // String을 Integer로 파싱
        Integer year;
        try {
            year = Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            log.error("숫자 파싱 실패: year={}", yearStr, e);
            throw new IllegalArgumentException("잘못된 연도 형식입니다: " + yearStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<List<MonthlyActualBalance>> response = assetService.getMonthlyActualBalancesByYear(userId, year);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PostMapping("/monthly-balance")
    @Operation(summary = "월별 실제 금액 저장", description = "특정 연도/월의 실제 금액을 저장합니다.")
    public UnifiedApiResponse<MonthlyActualBalance> saveMonthlyActualBalance(
            @Parameter(description = "연도")
            @RequestParam(value = "year") @NotNull String yearStr,
            @Parameter(description = "월 (1-12)")
            @RequestParam(value = "month") @NotNull String monthStr,
            @Parameter(description = "실제 금액")
            @RequestParam(value = "actualBalance") @NotNull String actualBalanceStr,
            @Parameter(description = "계산된 금액 (차이 계산용, 선택)")
            @RequestParam(value = "calculatedBalance", required = false) String calculatedBalanceStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("월별 실제 금액 저장 요청: year={}, month={}, actualBalanceStr={}, calculatedBalanceStr={}", 
            yearStr, monthStr, actualBalanceStr, calculatedBalanceStr);
        
        // String을 Integer로 파싱
        Integer year;
        Integer month;
        try {
            year = Integer.parseInt(yearStr);
            month = Integer.parseInt(monthStr);
        } catch (NumberFormatException e) {
            log.error("숫자 파싱 실패: year={}, month={}", yearStr, monthStr, e);
            throw new IllegalArgumentException("잘못된 연도 또는 월 형식입니다: year=" + yearStr + ", month=" + monthStr);
        }
        
        // String을 BigDecimal로 파싱
        BigDecimal actualBalance;
        try {
            actualBalance = new BigDecimal(actualBalanceStr);
        } catch (NumberFormatException e) {
            log.error("금액 파싱 실패: {}", actualBalanceStr, e);
            throw new IllegalArgumentException("잘못된 실제 금액 형식입니다: " + actualBalanceStr);
        }
        
        BigDecimal calculatedBalance = null;
        if (calculatedBalanceStr != null && !calculatedBalanceStr.trim().isEmpty()) {
            try {
                calculatedBalance = new BigDecimal(calculatedBalanceStr);
            } catch (NumberFormatException e) {
                log.error("계산된 금액 파싱 실패: {}", calculatedBalanceStr, e);
                throw new IllegalArgumentException("잘못된 계산된 금액 형식입니다: " + calculatedBalanceStr);
            }
        }
        
        log.info("파싱된 값: year={}, month={}, actualBalance={}, calculatedBalance={}", 
            year, month, actualBalance, calculatedBalance);
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<MonthlyActualBalance> response = assetService.saveMonthlyActualBalance(
            userId, year, month, actualBalance, calculatedBalance);
        
        log.info("월별 실제 금액 저장 완료: {}", response.getData());
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @PostMapping("/monthly-investment-seed-addition")
    @Operation(summary = "월별 투자시드 증액 저장", description = "특정 연도/월의 투자시드 증액을 저장합니다. 해당 월 순수 현금에서 차감되고 홈 총 시드에 가산됩니다.")
    public UnifiedApiResponse<MonthlyActualBalance> saveMonthlyInvestmentSeedAddition(
            @Parameter(description = "연도")
            @RequestParam(value = "year") @NotNull String yearStr,
            @Parameter(description = "월 (1-12)")
            @RequestParam(value = "month") @NotNull String monthStr,
            @Parameter(description = "투자시드 증액 (0 또는 비우면 해당 월 증액 삭제)")
            @RequestParam(value = "amount", required = false) String amountStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("월별 투자시드 증액 저장 요청: year={}, month={}, amountStr={}", yearStr, monthStr, amountStr);
        
        Integer year;
        Integer month;
        try {
            year = Integer.parseInt(yearStr);
            month = Integer.parseInt(monthStr);
        } catch (NumberFormatException e) {
            log.error("숫자 파싱 실패: year={}, month={}", yearStr, monthStr, e);
            throw new IllegalArgumentException("잘못된 연도 또는 월 형식입니다: year=" + yearStr + ", month=" + monthStr);
        }
        
        BigDecimal amount = null;
        if (amountStr != null && !amountStr.trim().isEmpty()) {
            try {
                amount = new BigDecimal(amountStr);
            } catch (NumberFormatException e) {
                log.error("금액 파싱 실패: {}", amountStr, e);
                throw new IllegalArgumentException("잘못된 금액 형식입니다: " + amountStr);
            }
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<MonthlyActualBalance> response = assetService.saveMonthlyInvestmentSeedAddition(userId, year, month, amount);
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @DeleteMapping("/monthly-balance")
    @Operation(summary = "월별 실제 금액 삭제", description = "특정 연도/월의 실제 금액을 삭제합니다.")
    public UnifiedApiResponse<Void> deleteMonthlyActualBalance(
            @Parameter(description = "연도")
            @RequestParam(value = "year") @NotNull String yearStr,
            @Parameter(description = "월 (1-12)")
            @RequestParam(value = "month") @NotNull String monthStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        log.info("월별 실제 금액 삭제 요청: year={}, month={}", yearStr, monthStr);
        
        // String을 Integer로 파싱
        Integer year;
        Integer month;
        try {
            year = Integer.parseInt(yearStr);
            month = Integer.parseInt(monthStr);
        } catch (NumberFormatException e) {
            log.error("숫자 파싱 실패: year={}, month={}", yearStr, monthStr, e);
            throw new IllegalArgumentException("잘못된 연도 또는 월 형식입니다: year=" + yearStr + ", month=" + monthStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<Void> response = assetService.deleteMonthlyActualBalance(userId, year, month);
        
        log.info("월별 실제 금액 삭제 완료");
        
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
}

