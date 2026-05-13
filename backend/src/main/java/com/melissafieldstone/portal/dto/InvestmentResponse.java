package com.melissafieldstone.portal.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class InvestmentResponse {
    private Integer investmentId;
    private String name;
    private LocalDate startDate;
    private LocalDate anticipatedEndDate;
    private LocalDate endDate;
    private String currentUpdate;
    private Integer projectProgress;
    private List<InvestorSummary> investors;
    private List<DocumentSummary> documents;

    @Data
    public static class InvestorSummary {
        private Integer investorId;
        private String firstName;
        private String lastName;
        private String email;
    }

    @Data
    public static class DocumentSummary {
        private Integer documentId;
        private String name;
        private String url;
    }
}
