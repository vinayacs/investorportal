package com.melissafieldstone.portal.repository;

import com.melissafieldstone.portal.entity.PropertyAnalysisCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyAnalysisCacheRepository extends JpaRepository<PropertyAnalysisCache, Long> {
    Optional<PropertyAnalysisCache> findByCacheKey(String cacheKey);
    void deleteByCacheKey(String cacheKey);
    PropertyAnalysisCache findTopByOrderByLastAccessedAtAsc();
}
