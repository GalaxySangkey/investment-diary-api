package com.investmentdiary.repository;

import com.investmentdiary.entity.StockTickerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockTickerMappingRepository extends JpaRepository<StockTickerMapping, Long> {
    
    // 종목코드로 조회
    Optional<StockTickerMapping> findByStockCode(String stockCode);
    
    // 티커로 조회
    Optional<StockTickerMapping> findByTicker(String ticker);
    
    // 종목명으로 조회
    List<StockTickerMapping> findByStockNameContaining(String stockName);
    
    // 활성화된 매핑만 조회
    List<StockTickerMapping> findByIsActiveTrue();
    
    // 종목코드 리스트로 조회
    List<StockTickerMapping> findByStockCodeIn(List<String> stockCodes);
}

