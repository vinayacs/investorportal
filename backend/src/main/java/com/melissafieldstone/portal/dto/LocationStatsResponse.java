package com.melissafieldstone.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class LocationStatsResponse {
    private long totalPageVisits;
    private long totalLoginEvents;
    private long uniqueCountries;
    private long uniqueCities;
    private List<NameCount> topCountries;
    private List<NameCount> topCities;
    private List<NameCount> pageBreakdown;

    @Data
    @AllArgsConstructor
    public static class NameCount {
        private String name;
        private long count;
    }
}
