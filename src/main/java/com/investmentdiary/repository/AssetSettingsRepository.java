package com.investmentdiary.repository;

import com.investmentdiary.entity.AssetSettings;
import com.investmentdiary.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetSettingsRepository extends JpaRepository<AssetSettings, Long> {
    Optional<AssetSettings> findByUser(User user);
    Optional<AssetSettings> findByUserId(Long userId);
}

