package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.Investor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvestorRepository extends JpaRepository<Investor, Integer> {
    Optional<Investor> findByEmail(String email);
}
