package com.melissafieldstone.portal.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginResponse {
    private String token;
    private String role;
    private String firstName;
    private String lastName;
    private boolean mfaPending;
    private String mfaToken;
    private String mfaType;
    private String deviceToken; // returned after MFA verify when rememberDevice=true

    public LoginResponse(String token, String role, String firstName, String lastName) {
        this.token = token;
        this.role = role;
        this.firstName = firstName;
        this.lastName = lastName;
        this.mfaPending = false;
    }

    public static LoginResponse pending(String mfaToken, String mfaType) {
        LoginResponse r = new LoginResponse();
        r.mfaPending = true;
        r.mfaToken = mfaToken;
        r.mfaType = mfaType;
        return r;
    }
}
