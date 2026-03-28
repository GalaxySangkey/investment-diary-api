package com.investmentdiary.repository;

import com.investmentdiary.entity.FixedExpense;
import com.investmentdiary.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixedExpenseRepository extends JpaRepository<FixedExpense, Long> {
    List<FixedExpense> findByUser(User user);
    List<FixedExpense> findByUserId(Long userId);
    void deleteByUser(User user);
    void deleteByUserId(Long userId);
}

