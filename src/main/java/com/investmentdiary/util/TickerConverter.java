package com.investmentdiary.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 한국 주식 코드를 Yahoo Finance 티커로 변환하는 유틸리티
 */
@Slf4j
@Component
public class TickerConverter {
    
    /**
     * 한국 주식 코드를 Yahoo Finance 티커로 변환
     * @param stockCode 종목코드 (6자리 숫자) 또는 이미 티커 형식
     * @param market 시장 (KOSPI 또는 KOSDAQ, null이면 자동 판단)
     * @return Yahoo Finance 티커 (예: 005930.KS, AAPL)
     */
    public String convertToYahooTicker(String stockCode, String market) {
        if (stockCode == null || stockCode.trim().isEmpty()) {
            return null;
        }
        
        // 이미 티커 형식인 경우 그대로 반환
        if (stockCode.contains(".")) {
            return stockCode;
        }
        
        // 숫자가 아닌 경우 (미국 주식 등) 그대로 반환
        if (!stockCode.matches("^[0-9]+$")) {
            log.debug("Non-numeric stock code detected, returning as-is: {}", stockCode);
            return stockCode;
        }
        
        // 숫자인 경우 한국 주식으로 처리
        try {
            // 6자리 숫자로 패딩
            String paddedCode = String.format("%06d", Integer.parseInt(stockCode));
            
            // 시장이 지정되지 않은 경우 KOSPI로 가정 (일반적으로 KOSPI가 더 많음)
            if (market == null || market.isEmpty()) {
                market = "KOSPI";
            }
            
            // KOSPI는 .KS, KOSDAQ은 .KQ
            String suffix = "KOSPI".equalsIgnoreCase(market) ? ".KS" : ".KQ";
            
            return paddedCode + suffix;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse stock code as number: {}, returning as-is", stockCode);
            return stockCode;
        }
    }
    
    /**
     * Yahoo Finance 티커를 한국 주식 코드로 변환
     * @param ticker Yahoo Finance 티커 (예: 005930.KS)
     * @return 종목코드 (6자리 숫자 문자열)
     */
    public String convertToStockCode(String ticker) {
        if (ticker == null || !ticker.contains(".")) {
            return ticker;
        }
        
        // .KS 또는 .KQ 제거
        String code = ticker.split("\\.")[0];
        
        // 앞의 0 제거
        try {
            return String.valueOf(Integer.parseInt(code));
        } catch (NumberFormatException e) {
            log.warn("티커를 종목코드로 변환 실패: {}", ticker);
            return code;
        }
    }
    
    /**
     * 통화쌍을 Yahoo Finance 티커로 변환
     * @param baseCurrency 기준 통화 (예: USD)
     * @param quoteCurrency 상대 통화 (예: KRW)
     * @return Yahoo Finance 티커 (예: USDKRW=X)
     */
    public String convertToCurrencyTicker(String baseCurrency, String quoteCurrency) {
        if (baseCurrency == null || quoteCurrency == null) {
            return null;
        }
        return baseCurrency + quoteCurrency + "=X";
    }
}

