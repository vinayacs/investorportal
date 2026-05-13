package com.melissafieldstone.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddDocumentRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String url;
}
