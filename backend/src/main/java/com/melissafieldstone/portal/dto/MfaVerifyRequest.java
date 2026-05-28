package com.melissafieldstone.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaVerifyRequest {
    @NotBlank
    private String mfaToken;
    @NotBlank
    private String code;
    private boolean rememberDevice;
}
