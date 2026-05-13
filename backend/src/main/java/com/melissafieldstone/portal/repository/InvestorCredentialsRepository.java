package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.InvestorCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvestorCredentialsRepository extends JpaRepository<InvestorCredentials, Integer> {
    Optional<InvestorCredentials> findByUsername(String username);
    Optional<InvestorCredentials> findByResetToken(String resetToken);
}
