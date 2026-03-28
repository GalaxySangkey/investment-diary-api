package com.investmentdiary.repository;

import com.investmentdiary.entity.PeriodExpense;
import com.investmentdiary.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PeriodExpenseRepository extends JpaRepository<PeriodExpense, Long> {
    List<PeriodExpense> findByUser(User user);
    List<PeriodExpense> findByUserId(Long userId);
    void deleteByUser(User user);
    void deleteByUserId(Long userId);
}

