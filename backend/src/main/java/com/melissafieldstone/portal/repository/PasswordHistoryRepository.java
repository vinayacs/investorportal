package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {
    List<PasswordHistory> findTop3ByUserTypeAndUserIdOrderByCreatedAtDesc(String userType, Integer userId);
}
