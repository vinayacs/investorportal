package com.melissafieldstone.portal.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.melissafieldstone.portal.entity.PropertyAnalysisCache;
import com.melissafieldstone.portal.repository.PropertyAnalysisCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PropertyAnalysisCacheService {

    private static final int MAX_CACHE_SIZE = 1000;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PropertyAnalysisCacheRepository repo;
    private final ObjectMapper objectMapper;

    /** Returns cached result and updates its last-accessed timestamp. */
    @Transactional
    public Optional<Map<String, Object>> get(String cacheKey) {
        return repo.findByCacheKey(cacheKey).flatMap(entry -> {
            entry.setLastAccessedAt(LocalDateTime.now());
            repo.save(entry);
            try {
                return Optional.of(objectMapper.readValue(entry.getResultJson(), MAP_TYPE));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Stores a result. Evicts the LRU entry if the cache is at capacity.
     * Only stores results with at least one appraisal year (successful lookups).
     */
    @Transactional
    public void put(String cacheKey, Map<String, Object> result, String addressDisplay, String county) {
        Object years = result.get("years");
        if (!(years instanceof java.util.List<?> list) || list.isEmpty()) return;

        try {
            String json = objectMapper.writeValueAsString(result);

            // Evict LRU entry if at capacity
            if (repo.count() >= MAX_CACHE_SIZE) {
                PropertyAnalysisCache lru = repo.findTopByOrderByLastAccessedAtAsc();
                if (lru != null) repo.delete(lru);
            }

            PropertyAnalysisCache existing = repo.findByCacheKey(cacheKey).orElse(null);
            if (existing != null) {
                existing.setResultJson(json);
                existing.setCachedAt(LocalDateTime.now());
                existing.setLastAccessedAt(LocalDateTime.now());
                repo.save(existing);
            } else {
                PropertyAnalysisCache entry = new PropertyAnalysisCache();
                entry.setCacheKey(cacheKey);
                entry.setResultJson(json);
                entry.setCachedAt(LocalDateTime.now());
                entry.setLastAccessedAt(LocalDateTime.now());
                entry.setAddressDisplay(addressDisplay);
                entry.setCounty(county);
                repo.save(entry);
            }
        } catch (Exception ignored) {}
    }

    /** Removes a cached entry so the next request re-fetches fresh data. */
    @Transactional
    public void evict(String cacheKey) {
        repo.deleteByCacheKey(cacheKey);
    }

    public static String buildAddressKey(String address) {
        return "addr:" + address.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    public static String buildPropertyIdKey(String county, String propertyId) {
        return "id:" + county.toLowerCase() + ":" + propertyId.toLowerCase().trim();
    }
}
