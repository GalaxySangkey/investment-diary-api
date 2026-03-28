package com.investmentdiary.repository;

import com.investmentdiary.entity.PeriodIncome;
import com.investmentdiary.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PeriodIncomeRepository extends JpaRepository<PeriodIncome, Long> {
    List<PeriodIncome> findByUser(User user);
    List<PeriodIncome> findByUserId(Long userId);
    void deleteByUser(User user);
    void deleteByUserId(Long userId);
}

