package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "scraper_logs")
public class ScraperLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime ts = LocalDateTime.now();

    private String county;
    private String city;

    @Column(nullable = false, length = 20)
    private String searchType;

    @Column(length = 500)
    private String input;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false)
    private int durationMs;

    @Column(length = 100)
    private String propertyId;

    @Column(length = 1000)
    private String errorMsg;
}
