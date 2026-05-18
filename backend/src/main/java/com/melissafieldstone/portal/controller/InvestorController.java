package com.melissafieldstone.portal.controller;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.service.AuthService;
import com.melissafieldstone.portal.service.InvestmentService;
import com.melissafieldstone.portal.service.InvestorService;
import com.melissafieldstone.portal.service.MfaService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/investor")
@RequiredArgsConstructor
public class InvestorController {

    private final InvestorService investorService;
    private final InvestmentService investmentService;
    private final AuthService authService;
    private final MfaService mfaService;

    @GetMapping("/me")
    public ResponseEntity<InvestorResponse> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(investorService.getMyProfile(auth.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<InvestorResponse> updateMyProfile(Authentication auth,
                                                             @RequestBody InvestorUpdateRequest request) {
        return ResponseEntity.ok(investorService.updateMyProfile(auth.getName(), request));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(Authentication auth,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/investments")
    public ResponseEntity<List<InvestmentResponse>> getMyInvestments(Authentication auth) {
        return ResponseEntity.ok(investmentService.getInvestmentsForInvestor(auth.getName()));
    }

    @GetMapping("/documents/{documentId}")
    public void streamDocument(@PathVariable Integer documentId,
                               Authentication auth,
                               HttpServletResponse response) throws IOException {
        investmentService.streamDocument(documentId, auth.getName(), response);
    }

    // ── MFA management ────────────────────────────────────────────────────────

    @GetMapping("/me/mfa/status")
    public ResponseEntity<MfaStatusResponse> getMfaStatus(Authentication auth) {
        return ResponseEntity.ok(mfaService.getStatusForInvestor(auth.getName()));
    }

    @PostMapping("/me/mfa/setup/totp")
    public ResponseEntity<MfaSetupTotpResponse> setupTotp(Authentication auth) {
        return ResponseEntity.ok(mfaService.setupTotpForInvestor(auth.getName()));
    }

    @PostMapping("/me/mfa/send-otp")
    public ResponseEntity<Void> sendMfaOtp(Authentication auth) {
        mfaService.sendOtpForInvestor(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/mfa/enable")
    public ResponseEntity<Void> enableMfa(Authentication auth,
                                          @Valid @RequestBody EnableMfaRequest request) {
        mfaService.enableMfaForInvestor(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/mfa/disable")
    public ResponseEntity<Void> disableMfa(Authentication auth,
                                           @Valid @RequestBody DisableMfaRequest request) {
        mfaService.disableMfaForInvestor(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
