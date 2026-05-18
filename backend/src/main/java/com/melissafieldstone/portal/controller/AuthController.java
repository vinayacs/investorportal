package com.melissafieldstone.portal.controller;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.service.AuthService;
import com.melissafieldstone.portal.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MfaService mfaService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(Map.of("message", "If that email exists, a reset link has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<LoginResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(mfaService.verifyAndCompleteLogin(request));
    }

    @PostMapping("/mfa/select-method")
    public ResponseEntity<Void> selectMfaMethod(@RequestBody Map<String, String> body) {
        mfaService.selectMethod(body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/resend-otp")
    public ResponseEntity<Void> resendMfaOtp(@RequestBody Map<String, String> body) {
        String mfaToken = body.get("mfaToken");
        if (mfaToken == null || mfaToken.isBlank()) throw new RuntimeException("MFA token is required.");
        mfaService.resendOtp(mfaToken);
        return ResponseEntity.noContent().build();
    }
}
