package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.InvestorLoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InvestorLoginLogRepository extends JpaRepository<InvestorLoginLog, Integer> {
    List<InvestorLoginLog> findAllByOrderByLoginTimestampDesc();
}
