package com.melissafieldstone.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateInvestmentRequest {
    @NotBlank
    private String name;

    @NotNull
    private LocalDate startDate;

    private LocalDate anticipatedEndDate;
    private LocalDate endDate;
    private List<Integer> investorIds;
    private String currentUpdate;
    private Integer projectProgress;
}
