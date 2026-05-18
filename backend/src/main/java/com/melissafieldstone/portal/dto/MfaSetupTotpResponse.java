package com.melissafieldstone.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MfaSetupTotpResponse {
    private String secret;
    private String otpauthUrl;
}
