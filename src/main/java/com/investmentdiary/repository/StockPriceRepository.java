package com.investmentdiary.repository;

import com.investmentdiary.entity.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {
    
    // 티커와 날짜로 조회
    Optional<StockPrice> findByTickerAndPriceDate(String ticker, LocalDate priceDate);
    
    // 종목코드와 날짜로 조회
    Optional<StockPrice> findByStockCodeAndPriceDate(String stockCode, LocalDate priceDate);
    
    // 특정 날짜의 모든 종가 조회
    List<StockPrice> findByPriceDate(LocalDate priceDate);
    
    // 티커별 최신 종가 조회
    @Query("SELECT sp FROM StockPrice sp WHERE sp.ticker = :ticker AND sp.priceDate = (SELECT MAX(sp2.priceDate) FROM StockPrice sp2 WHERE sp2.ticker = :ticker)")
    Optional<StockPrice> findLatestByTicker(@Param("ticker") String ticker);
    
    // 종목코드별 최신 종가 조회
    @Query("SELECT sp FROM StockPrice sp WHERE sp.stockCode = :stockCode AND sp.priceDate = (SELECT MAX(sp2.priceDate) FROM StockPrice sp2 WHERE sp2.stockCode = :stockCode)")
    Optional<StockPrice> findLatestByStockCode(@Param("stockCode") String stockCode);
    
    // 티커 리스트와 날짜로 조회
    List<StockPrice> findByTickerInAndPriceDate(List<String> tickers, LocalDate priceDate);
    
    // 종목코드 리스트와 날짜로 조회
    List<StockPrice> findByStockCodeInAndPriceDate(List<String> stockCodes, LocalDate priceDate);
    
    // 종목코드 리스트와 날짜 범위로 일괄 조회 (캘린더 최적화용)
    @Query("SELECT sp FROM StockPrice sp WHERE sp.stockCode IN :stockCodes AND sp.priceDate BETWEEN :startDate AND :endDate ORDER BY sp.stockCode, sp.priceDate")
    List<StockPrice> findByStockCodesAndDateRange(
        @Param("stockCodes") List<String> stockCodes,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}

