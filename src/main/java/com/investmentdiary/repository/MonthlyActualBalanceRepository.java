package com.investmentdiary.repository;

import com.investmentdiary.entity.MonthlyActualBalance;
import com.investmentdiary.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonthlyActualBalanceRepository extends JpaRepository<MonthlyActualBalance, Long> {
    Optional<MonthlyActualBalance> findByUserAndYearAndMonth(User user, Integer year, Integer month);
    Optional<MonthlyActualBalance> findByUserIdAndYearAndMonth(Long userId, Integer year, Integer month);
    List<MonthlyActualBalance> findByUserAndYear(User user, Integer year);
    List<MonthlyActualBalance> findByUserIdAndYear(Long userId, Integer year);
    List<MonthlyActualBalance> findByUser(User user);
}

