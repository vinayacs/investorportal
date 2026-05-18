package com.melissafieldstone.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DisableMfaRequest {
    @NotBlank
    private String type; // "EMAIL" or "TOTP"
    @NotBlank
    private String code;
}
