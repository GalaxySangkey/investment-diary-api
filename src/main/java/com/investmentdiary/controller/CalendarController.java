package com.investmentdiary.controller;

import com.investmentdiary.dto.ApiResponse;
import com.investmentdiary.dto.UnifiedApiResponse;
import com.investmentdiary.entity.InvestmentRecord;
import com.investmentdiary.security.JwtTokenProvider;
import com.investmentdiary.service.CalendarService;
import com.investmentdiary.util.ResponseConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "캘린더", description = "캘린더 관련 API")
public class CalendarController {
    
    private final CalendarService calendarService;
    private final JwtTokenProvider jwtTokenProvider;
    
    public CalendarController(CalendarService calendarService, JwtTokenProvider jwtTokenProvider) {
        this.calendarService = calendarService;
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
    @Operation(summary = "캘린더 데이터 조회", description = "특정 년월의 캘린더 데이터를 조회합니다.")
    public UnifiedApiResponse<Map<String, Object>> getCalendarData(
            @Parameter(description = "년도")
            @RequestParam(value = "year", required = false, defaultValue = "2024") int year,
            @Parameter(description = "월")
            @RequestParam(value = "month", required = false, defaultValue = "1") int month,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<Map<String, Object>> response = calendarService.getCalendarData(userId, year, month);
        
        // 기존 ApiResponse를 UnifiedApiResponse로 변환
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/{date}/records")
    @Operation(summary = "특정 날짜 투자 기록 조회", description = "특정 날짜의 투자 기록을 조회합니다.")
    public UnifiedApiResponse<List<InvestmentRecord>> getRecordsByDate(
            @Parameter(description = "날짜 (YYYY-MM-DD)")
            @PathVariable(value = "date") String dateStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        // String을 LocalDate로 변환
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. YYYY-MM-DD 형식을 사용해주세요: " + dateStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<List<InvestmentRecord>> response = calendarService.getRecordsByDate(userId, date);
        
        // 기존 ApiResponse를 UnifiedApiResponse로 변환
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
    
    @GetMapping("/{date}/portfolio")
    @Operation(summary = "특정 날짜 보유 종목 조회", description = "특정 날짜의 보유 종목 목록과 상세 정보를 조회합니다.")
    public UnifiedApiResponse<Map<String, Object>> getDailyPortfolio(
            @Parameter(description = "날짜 (YYYY-MM-DD)")
            @PathVariable(value = "date") String dateStr,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        // String을 LocalDate로 변환
        LocalDate date;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. YYYY-MM-DD 형식을 사용해주세요: " + dateStr);
        }
        
        Long userId = getUserIdFromRequest(httpRequest);
        ApiResponse<Map<String, Object>> response = calendarService.getDailyPortfolio(userId, date);
        
        // 기존 ApiResponse를 UnifiedApiResponse로 변환
        return ResponseConverter.convert(response, httpRequest.getRequestURI());
    }
}




