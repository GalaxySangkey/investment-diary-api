package com.investmentdiary.repository;

import com.investmentdiary.entity.StockDividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockDividendRepository extends JpaRepository<StockDividend, Long> {
    
    // 티커와 배당일로 조회
    Optional<StockDividend> findByTickerAndDividendDate(String ticker, LocalDate dividendDate);
    
    // 종목코드와 배당일로 조회
    Optional<StockDividend> findByStockCodeAndDividendDate(String stockCode, LocalDate dividendDate);
    
    // 티커별 최신 배당 조회
    @Query("SELECT sd FROM StockDividend sd WHERE sd.ticker = :ticker AND sd.dividendDate = (SELECT MAX(sd2.dividendDate) FROM StockDividend sd2 WHERE sd2.ticker = :ticker)")
    Optional<StockDividend> findLatestByTicker(@Param("ticker") String ticker);
    
    // 특정 기간의 배당 조회
    List<StockDividend> findByTickerAndDividendDateBetween(String ticker, LocalDate startDate, LocalDate endDate);
    
    // 티커 리스트와 배당일로 조회
    List<StockDividend> findByTickerInAndDividendDate(List<String> tickers, LocalDate dividendDate);
    
    // 티커로 최신 배당 정보 조회 (날짜 내림차순)
    List<StockDividend> findByTickerOrderByDividendDateDesc(String ticker);
}

