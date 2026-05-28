package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.TrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    Optional<TrustedDevice> findByDeviceTokenAndUsername(String deviceToken, String username);
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
