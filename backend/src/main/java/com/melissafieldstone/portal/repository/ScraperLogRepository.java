package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.ScraperLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ScraperLogRepository extends JpaRepository<ScraperLog, Long> {

    List<ScraperLog> findTop100ByOrderByTsDesc();

    @Query("""
        SELECT s FROM ScraperLog s WHERE s.ts >= :since ORDER BY s.ts DESC
        """)
    List<ScraperLog> findSince(@Param("since") LocalDateTime since);

    @Query("""
        SELECT s.county, COUNT(s), SUM(CASE WHEN s.success = true THEN 1 ELSE 0 END), AVG(s.durationMs)
        FROM ScraperLog s WHERE s.ts >= :since
        GROUP BY s.county ORDER BY COUNT(s) DESC
        """)
    List<Object[]> countyStatsSince(@Param("since") LocalDateTime since);
}
