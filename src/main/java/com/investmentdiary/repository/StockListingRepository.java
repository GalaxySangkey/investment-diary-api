package com.investmentdiary.repository;

import com.investmentdiary.entity.StockListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockListingRepository extends JpaRepository<StockListing, Long> {

    /**
     * 종목코드 + 국가로 조회
     */
    Optional<StockListing> findByStockCodeAndCountry(String stockCode, String country);

    /**
     * 종목명 또는 종목코드로 검색 (LIKE) - Native Query 사용
     * 관련도 정렬: 정확 일치 > 시작 일치 > 포함 일치
     */
    @Query(value = "SELECT * FROM stock_listings s WHERE s.is_active = true " +
           "AND (s.stock_name LIKE CONCAT('%', :query, '%') OR s.stock_code LIKE CONCAT('%', :query, '%')) " +
           "AND (:country IS NULL OR s.country = :country) " +
           "ORDER BY " +
           "CASE WHEN s.stock_code = :query THEN 0 " +
           "     WHEN s.stock_code LIKE CONCAT(:query, '%') THEN 1 " +
           "     WHEN s.stock_name = :query THEN 2 " +
           "     WHEN s.stock_name LIKE CONCAT(:query, '%') THEN 3 " +
           "     ELSE 4 END, " +
           "s.stock_name ASC",
           nativeQuery = true)
    List<StockListing> searchByQueryAndCountry(
            @Param("query") String query,
            @Param("country") String country);

    /**
     * 국가별 활성 종목 수 조회
     */
    long countByCountryAndIsActive(String country, Boolean isActive);

    /**
     * 국가별 전체 종목 조회 (활성만)
     */
    List<StockListing> findByCountryAndIsActive(String country, Boolean isActive);

    /**
     * 벌크 업데이트: 특정 국가의 모든 종목을 비활성화
     */
    @Modifying
    @Query("UPDATE StockListing s SET s.isActive = false WHERE s.country = :country")
    void deactivateAllByCountry(@Param("country") String country);
}
