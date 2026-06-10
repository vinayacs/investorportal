package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "property_analysis_cache", indexes = {
    @Index(name = "idx_pac_cache_key", columnList = "cache_key", unique = true),
    @Index(name = "idx_pac_last_accessed", columnList = "last_accessed_at")
})
public class PropertyAnalysisCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cache_key", unique = true, nullable = false, length = 500)
    private String cacheKey;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "cached_at", nullable = false)
    private LocalDateTime cachedAt;

    @Column(name = "last_accessed_at", nullable = false)
    private LocalDateTime lastAccessedAt;

    @Column(name = "address_display", length = 500)
    private String addressDisplay;

    @Column(length = 100)
    private String county;
}
