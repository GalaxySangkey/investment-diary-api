package com.investmentdiary.repository;

import com.investmentdiary.entity.FixedIncome;
import com.investmentdiary.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FixedIncomeRepository extends JpaRepository<FixedIncome, Long> {
    List<FixedIncome> findByUser(User user);
    List<FixedIncome> findByUserId(Long userId);
    void deleteByUser(User user);
    void deleteByUserId(Long userId);
}

