package com.investmentdiary.controller;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.util.ResponseConverter;
import com.investmentdiary.dto.investment.BuyInvestmentRequest;
import com.investmentdiary.dto.investment.DeleteInvestmentRequest;
import com.investmentdiary.dto.investment.SellInvestmentRequest;
import com.investmentdiary.dto.investment.UpdateInvestmentRequest;
import com.investmentdiary.entity.InvestmentRecord;
import com.investmentdiary.service.InvestmentService;
import com.investmentdiary.security.JwtTokenProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/investments")
@Tag(name = "투자 기록", description = "투자 기록 관리 API")
public class InvestmentController {
    
    private final InvestmentService investmentService;
    private final JwtTokenProvider jwtTokenProvider;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public InvestmentController(InvestmentService investmentService, JwtTokenProvider jwtTokenProvider) {
        this.investmentService = investmentService;
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
    
    @GetMapping
    @Operation(summary = "투자 기록 목록 조회", description = "사용자의 투자 기록을 페이징하여 조회합니다.")
    public UnifiedApiResponse<Page<InvestmentRecord>> getInvestments(
            @Parameter(description = "투자 유형 (buy/sell)")
            @RequestParam(value = "type", required = false) String type,
            @Parameter(description = "시작 날짜")
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료 날짜")
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<Page<InvestmentRecord>> response = investmentService.getInvestments(userId, pageable);
        
        // 기존 ApiResponse를 UnifiedApiResponse로 변환
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "투자 기록 상세 조회", description = "특정 투자 기록의 상세 정보를 조회합니다.")
    public ApiResponse<Object> getInvestment(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        Object investment = investmentService.getInvestment(userId, id);
        return ApiResponse.success(investment);
    }
    
    @PostMapping("/buy")
    @Operation(summary = "매수 기록 생성", description = "새로운 매수 기록을 생성합니다.")
    public ApiResponse<Object> createBuyInvestment(
            @Valid @RequestBody BuyInvestmentRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        Object investment = investmentService.createBuyInvestment(userId, request);
        return ApiResponse.success(investment, "매수 기록이 생성되었습니다.");
    }
    
    @PostMapping("/sell")
    @Operation(summary = "매도 기록 생성", description = "새로운 매도 기록을 생성합니다.")
    public ApiResponse<Object> createSellInvestment(
            @Valid @RequestBody SellInvestmentRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        Object investment = investmentService.createSellInvestment(userId, request);
        return ApiResponse.success(investment, "매도 기록이 생성되었습니다.");
    }
    
    @PostMapping("/update")
    @Operation(summary = "투자 기록 수정", description = "기존 투자 기록을 수정합니다.")
    public ApiResponse<Object> updateInvestment(
            @Valid @RequestBody UpdateInvestmentRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        // UpdateInvestmentRequest를 Map으로 변환
        Map<String, Object> updates = new java.util.HashMap<>();
        if (request.getAssetType() != null) {
            updates.put("assetType", request.getAssetType());
        }
        if (request.getStockName() != null) {
            updates.put("stockName", request.getStockName());
        }
        if (request.getStockCode() != null) {
            updates.put("stockCode", request.getStockCode());
        }
        if (request.getCurrencyPair() != null) {
            updates.put("currencyPair", request.getCurrencyPair());
        }
        if (request.getBaseCurrency() != null) {
            updates.put("baseCurrency", request.getBaseCurrency());
        }
        if (request.getQuoteCurrency() != null) {
            updates.put("quoteCurrency", request.getQuoteCurrency());
        }
        if (request.getExchangeRate() != null) {
            updates.put("exchangeRate", request.getExchangeRate());
        }
        if (request.getInvestmentRatio() != null) {
            updates.put("investmentRatio", request.getInvestmentRatio());
        }
        if (request.getQuantity() != null) {
            updates.put("quantity", request.getQuantity());
        }
        if (request.getPricePerShare() != null) {
            updates.put("pricePerShare", request.getPricePerShare());
        }
        if (request.getTotalAmount() != null) {
            updates.put("totalAmount", request.getTotalAmount());
        }
        if (request.getBuyReason() != null) {
            updates.put("buyReason", request.getBuyReason());
        }
        if (request.getSellReason() != null) {
            updates.put("sellReason", request.getSellReason());
        }
        if (request.getSellQuantity() != null) {
            updates.put("sellQuantity", request.getSellQuantity());
        }
        if (request.getSellRatio() != null) {
            updates.put("sellRatio", request.getSellRatio());
        }
        if (request.getRealizedProfitRate() != null) {
            updates.put("realizedProfitRate", request.getRealizedProfitRate());
        }
        if (request.getSellPrice() != null) {
            updates.put("sellPrice", request.getSellPrice());
        }
        
        ApiResponse<InvestmentRecord> response = investmentService.updateInvestment(userId, request.getId(), updates);
        return ApiResponse.success(response.getData(), "투자 기록이 수정되었습니다.");
    }
    
    @PostMapping("/delete")
    @Operation(summary = "투자 기록 삭제", description = "투자 기록을 삭제합니다.")
    public ApiResponse<Void> deleteInvestment(
            @Valid @RequestBody DeleteInvestmentRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        investmentService.deleteInvestment(userId, request.getId());
        return ApiResponse.success(null, "투자 기록이 삭제되었습니다.");
    }
} 