package com.melissafieldstone.portal.dto;

import lombok.Data;
import java.util.List;

@Data
public class ScraperMonitoringResponse {

    @Data
    public static class Stats {
        private long total7d;
        private long success7d;
        private long errors7d;
        private double successRate7d;
        private long avgDurationMs7d;
    }

    @Data
    public static class CountySummary {
        private String county;
        private long total;
        private long successCount;
        private double successRate;
        private long avgDurationMs;
    }

    private Stats stats;
    private List<CountySummary> countySummary;
    private List<ScraperLogResponse> recentLogs;
}
