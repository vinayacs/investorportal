package com.melissafieldstone.portal.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "page_visits")
public class PageVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String page;
    private String ipAddress;
    private String city;
    private String country;
    private String region;

    @Column(length = 512)
    private String userAgent;

    private LocalDateTime visitedAt = LocalDateTime.now();
}
