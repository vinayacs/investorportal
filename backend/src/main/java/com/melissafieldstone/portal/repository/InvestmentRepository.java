package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.Investment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvestmentRepository extends JpaRepository<Investment, Integer> {
    List<Investment> findAllByDeletedFalse();
    List<Investment> findByInvestors_InvestorIdAndDeletedFalse(Integer investorId);
}
