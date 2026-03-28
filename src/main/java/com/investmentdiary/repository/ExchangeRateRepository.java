package com.investmentdiary.repository;

import com.investmentdiary.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {
    
    // 통화쌍과 날짜로 조회
    Optional<ExchangeRate> findByCurrencyPairAndRateDate(String currencyPair, LocalDate rateDate);
    
    // 기준 통화와 상대 통화, 날짜로 조회
    Optional<ExchangeRate> findByBaseCurrencyAndQuoteCurrencyAndRateDate(
        String baseCurrency, String quoteCurrency, LocalDate rateDate);
    
    // 통화쌍별 최신 환율 조회
    @Query("SELECT er FROM ExchangeRate er WHERE er.currencyPair = :currencyPair AND er.rateDate = (SELECT MAX(er2.rateDate) FROM ExchangeRate er2 WHERE er2.currencyPair = :currencyPair)")
    Optional<ExchangeRate> findLatestByCurrencyPair(@Param("currencyPair") String currencyPair);
    
    // 특정 날짜의 모든 환율 조회
    List<ExchangeRate> findByRateDate(LocalDate rateDate);
}

