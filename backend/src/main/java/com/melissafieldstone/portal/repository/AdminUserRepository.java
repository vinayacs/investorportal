package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Integer> {
    Optional<AdminUser> findByEmail(String email);
    Optional<AdminUser> findByResetToken(String resetToken);
    Optional<AdminUser> findByMfaPendingToken(String mfaPendingToken);
}
