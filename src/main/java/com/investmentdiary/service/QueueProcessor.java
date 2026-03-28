package com.investmentdiary.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmentdiary.entity.ApiRequestQueue;

/**
 * 큐 요청 처리기
 * 실제 비즈니스 로직을 실행
 */
@Service
public class QueueProcessor {
    
    private static final Logger log = LoggerFactory.getLogger(QueueProcessor.class);
    
    private final ObjectMapper objectMapper;
    
    // 필요한 서비스들을 주입받아 사용
    // 예: InvestmentService, PortfolioService 등
    
    public QueueProcessor() {
        this.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }
    
    /**
     * 요청 처리
     */
    public String processRequest(ApiRequestQueue request) {
        log.info("요청 처리: requestId={}, endpoint={}, method={}", 
            request.getRequestId(), request.getEndpoint(), request.getMethod());
        
        try {
            // 엔드포인트에 따라 적절한 서비스 호출
            Object result = routeRequest(request);
            
            // 결과를 JSON 문자열로 변환
            return objectMapper.writeValueAsString(result);
            
        } catch (Exception e) {
            log.error("요청 처리 실패: requestId={}, error={}", request.getRequestId(), e.getMessage(), e);
            throw new RuntimeException("요청 처리 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 엔드포인트에 따라 요청 라우팅
     */
    private Object routeRequest(ApiRequestQueue request) {
        String endpoint = request.getEndpoint();
        String method = request.getMethod();
        
        // TODO: 실제 비즈니스 로직 서비스 호출
        // 예시:
        // if (endpoint.startsWith("/api/v1/investment")) {
        //     return investmentService.handleRequest(request);
        // } else if (endpoint.startsWith("/api/v1/portfolio")) {
        //     return portfolioService.handleRequest(request);
        // }
        
        // 임시로 성공 응답 반환
        return java.util.Map.of(
            "success", true,
            "message", "요청이 처리되었습니다.",
            "endpoint", endpoint,
            "method", method
        );
    }
}

