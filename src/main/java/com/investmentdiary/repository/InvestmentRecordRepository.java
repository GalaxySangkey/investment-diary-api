package com.investmentdiary.repository;

import com.investmentdiary.entity.InvestmentRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentRecordRepository extends JpaRepository<InvestmentRecord, Long> {
    
    // 기본 조회 메서드
    List<InvestmentRecord> findByUserId(Long userId);
    Page<InvestmentRecord> findByUserId(Long userId, Pageable pageable);
    
    // 날짜별 조회
    List<InvestmentRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);
    List<InvestmentRecord> findByUserIdAndRecordDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    
    // 투자 유형별 조회
    List<InvestmentRecord> findByUserIdAndType(Long userId, InvestmentRecord.InvestmentType type);
    Page<InvestmentRecord> findByUserIdAndType(Long userId, InvestmentRecord.InvestmentType type, Pageable pageable);
    
    // 종목별 조회
    List<InvestmentRecord> findByUserIdAndStockCode(Long userId, String stockCode);
    List<InvestmentRecord> findByUserIdAndStockNameContaining(Long userId, String stockName);
    
    // 활성 투자 기록만 조회 (삭제되지 않은)
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.isDeleted = false")
    List<InvestmentRecord> findActiveRecordsByUserId(@Param("userId") Long userId);
    
    // 특정 기간 투자 기록 조회
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.recordDate BETWEEN :startDate AND :endDate AND ir.isDeleted = false ORDER BY ir.recordDate DESC")
    List<InvestmentRecord> findRecordsByDateRange(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    // 수익률별 조회
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.unrealizedProfitRate >= :minProfitRate AND ir.isDeleted = false")
    List<InvestmentRecord> findProfitableRecords(
        @Param("userId") Long userId,
        @Param("minProfitRate") BigDecimal minProfitRate
    );
    
    // 손실 기록 조회
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.unrealizedProfitRate < 0 AND ir.isDeleted = false")
    List<InvestmentRecord> findLossRecords(@Param("userId") Long userId);
    
    // 배당률별 조회
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.dividendRatio >= :minDividendRate AND ir.isDeleted = false")
    List<InvestmentRecord> findHighDividendRecords(
        @Param("userId") Long userId,
        @Param("minDividendRate") BigDecimal minDividendRate
    );
    
    // 투자 비율별 조회
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.investmentRatio >= :minRatio AND ir.isDeleted = false")
    List<InvestmentRecord> findHighRatioRecords(
        @Param("userId") Long userId,
        @Param("minRatio") BigDecimal minRatio
    );
    
    // 월별 투자 기록 조회
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND YEAR(ir.recordDate) = :year AND MONTH(ir.recordDate) = :month AND ir.isDeleted = false")
    List<InvestmentRecord> findRecordsByYearAndMonth(
        @Param("userId") Long userId,
        @Param("year") int year,
        @Param("month") int month
    );
    
    // 검색 기능
    @Query("SELECT ir FROM InvestmentRecord ir WHERE ir.user.id = :userId AND " +
           "(:type IS NULL OR ir.type = :type) AND " +
           "(:stockName IS NULL OR ir.stockName LIKE %:stockName%) AND " +
           "(:startDate IS NULL OR ir.recordDate >= :startDate) AND " +
           "(:endDate IS NULL OR ir.recordDate <= :endDate) AND " +
           "ir.isDeleted = false " +
           "ORDER BY ir.recordDate DESC")
    Page<InvestmentRecord> searchRecords(
        @Param("userId") Long userId,
        @Param("type") InvestmentRecord.InvestmentType type,
        @Param("stockName") String stockName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );
    
    // 통계 조회
    @Query("SELECT COUNT(ir) FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.isDeleted = false")
    long countActiveRecordsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(ir) FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.type = :type AND ir.isDeleted = false")
    long countRecordsByType(@Param("userId") Long userId, @Param("type") InvestmentRecord.InvestmentType type);
    
    @Query("SELECT SUM(ir.totalAmount) FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.type = 'BUY' AND ir.isDeleted = false")
    Optional<BigDecimal> getTotalInvestmentAmount(@Param("userId") Long userId);
    
    @Query("SELECT SUM(ir.realizedProfitAmount) FROM InvestmentRecord ir WHERE ir.user.id = :userId AND ir.type = 'SELL' AND ir.isDeleted = false")
    Optional<BigDecimal> getTotalRealizedProfit(@Param("userId") Long userId);
    
    // 포트폴리오 분석용 쿼리
    @Query("SELECT ir.stockCode, ir.stockName, SUM(ir.quantity) as totalQuantity, " +
           "AVG(ir.pricePerShare) as avgPrice, SUM(ir.totalAmount) as totalAmount " +
           "FROM InvestmentRecord ir " +
           "WHERE ir.user.id = :userId AND ir.type = 'BUY' AND ir.isDeleted = false " +
           "GROUP BY ir.stockCode, ir.stockName")
    List<Object[]> getPortfolioSummary(@Param("userId") Long userId);
    
    // 섹터별 분산 분석
    @Query("SELECT ir.stockCode, ir.stockName, SUM(ir.totalAmount) as totalAmount, " +
           "SUM(ir.totalAmount) * 100.0 / (SELECT SUM(ir2.totalAmount) FROM InvestmentRecord ir2 WHERE ir2.user.id = :userId AND ir2.type = 'BUY' AND ir2.isDeleted = false) as ratio " +
           "FROM InvestmentRecord ir " +
           "WHERE ir.user.id = :userId AND ir.type = 'BUY' AND ir.isDeleted = false " +
           "GROUP BY ir.stockCode, ir.stockName " +
           "ORDER BY totalAmount DESC")
    List<Object[]> getSectorDistribution(@Param("userId") Long userId);
    
    // 캘린더용 데이터 조회
    @Query("SELECT ir.recordDate, COUNT(ir) as recordCount, SUM(ir.totalAmount) as totalInvestment " +
           "FROM InvestmentRecord ir " +
           "WHERE ir.user.id = :userId AND ir.recordDate BETWEEN :startDate AND :endDate AND ir.isDeleted = false " +
           "GROUP BY ir.recordDate")
    List<Object[]> getCalendarData(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
    
    // 성능 최적화를 위한 인덱스 힌트
    @Query(value = "SELECT * FROM investment_records ir USE INDEX (idx_user_date) WHERE ir.user_id = :userId AND ir.record_date BETWEEN :startDate AND :endDate", nativeQuery = true)
    List<InvestmentRecord> findRecordsByDateRangeWithIndex(
        @Param("userId") Long userId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
} 