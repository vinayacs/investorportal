package com.melissafieldstone.portal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PageVisitResponse {
    private Long id;
    private String page;
    private String ipAddress;
    private String city;
    private String country;
    private String region;
    private String userAgent;
    private LocalDateTime visitedAt;
}
