package com.investmentdiary.controller;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.constants.ResponseCode;
import com.investmentdiary.util.ResponseConverter;
import com.investmentdiary.dto.portfolio.PortfolioSummaryResponse;
import com.investmentdiary.service.PortfolioService;
import com.investmentdiary.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "포트폴리오", description = "포트폴리오 관리 API")
public class PortfolioController {
    
    private final PortfolioService portfolioService;
    private final JwtTokenProvider jwtTokenProvider;
    
    // 명시적인 생성자 (Lombok @RequiredArgsConstructor 대신)
    public PortfolioController(PortfolioService portfolioService, JwtTokenProvider jwtTokenProvider) {
        this.portfolioService = portfolioService;
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
    
    @GetMapping("/summary")
    @Operation(summary = "포트폴리오 요약 조회", description = "사용자의 포트폴리오 요약 정보를 조회합니다.")
    public UnifiedApiResponse<PortfolioSummaryResponse> getPortfolioSummary(
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<PortfolioSummaryResponse> response = portfolioService.getPortfolioSummary(userId);
        
        // 기존 ApiResponse를 UnifiedApiResponse로 변환
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/settings")
    @Operation(summary = "포트폴리오 설정 조회", description = "사용자의 포트폴리오 설정을 조회합니다.")
    public ApiResponse<Object> getPortfolioSettings(
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        Object settings = portfolioService.getPortfolioSettings(userId);
        return ApiResponse.success(settings);
    }
    
    @PostMapping("/settings/update")
    @Operation(summary = "포트폴리오 설정 수정", description = "사용자의 포트폴리오 설정을 수정합니다.")
    public ApiResponse<Object> updatePortfolioSettings(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestBody Object settings) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        // Object를 PortfolioSettings로 변환 (임시로 null 처리)
        com.investmentdiary.entity.PortfolioSettings portfolioSettings = null;
        ApiResponse<com.investmentdiary.entity.PortfolioSettings> response = portfolioService.updatePortfolioSettings(userId, portfolioSettings);
        return ApiResponse.success(response.getData(), "포트폴리오 설정이 수정되었습니다.");
    }
} 