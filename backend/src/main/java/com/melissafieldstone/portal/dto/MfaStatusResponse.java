package com.melissafieldstone.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MfaStatusResponse {
    private boolean emailEnabled;
    private boolean totpEnabled;
}
