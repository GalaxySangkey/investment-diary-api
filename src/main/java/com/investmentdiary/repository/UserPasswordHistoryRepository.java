package com.investmentdiary.repository;

import com.investmentdiary.entity.UserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserPasswordHistoryRepository extends JpaRepository<UserPasswordHistory, Long> {

    List<UserPasswordHistory> findTop2ByUser_IdOrderByCreatedAtDesc(Long userId);
}
