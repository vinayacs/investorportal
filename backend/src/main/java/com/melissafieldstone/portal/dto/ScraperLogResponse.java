package com.melissafieldstone.portal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScraperLogResponse {
    private Long id;
    private LocalDateTime ts;
    private String county;
    private String city;
    private String searchType;
    private String input;
    private boolean success;
    private int durationMs;
    private String propertyId;
    private String errorMsg;
}
