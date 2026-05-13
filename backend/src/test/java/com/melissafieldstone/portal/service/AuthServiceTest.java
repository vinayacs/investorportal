package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.ForgotPasswordRequest;
import com.melissafieldstone.portal.dto.LoginRequest;
import com.melissafieldstone.portal.dto.LoginResponse;
import com.melissafieldstone.portal.dto.ResetPasswordRequest;
import com.melissafieldstone.portal.entity.AdminUser;
import com.melissafieldstone.portal.entity.Investor;
import com.melissafieldstone.portal.entity.InvestorCredentials;
import com.melissafieldstone.portal.repository.AdminUserRepository;
import com.melissafieldstone.portal.repository.InvestorCredentialsRepository;
import com.melissafieldstone.portal.repository.InvestorLoginLogRepository;
import com.melissafieldstone.portal.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock InvestorCredentialsRepository credentialsRepo;
    @Mock AdminUserRepository adminUserRepo;
    @Mock InvestorLoginLogRepository loginLogRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock EmailService emailService;
    @Mock HttpServletRequest httpRequest;

    @InjectMocks AuthService authService;

    private Investor investor;
    private InvestorCredentials credentials;

    @BeforeEach
    void setUp() {
        investor = new Investor();
        investor.setInvestorId(1);
        investor.setFirstName("Jane");
        investor.setLastName("Doe");
        investor.setEmail("jane@example.com");

        credentials = new InvestorCredentials();
        credentials.setUsername("jane@example.com");
        credentials.setPasswordHash("hashed");
        credentials.setRole("INVESTOR");
        credentials.setIsActive(true);
        credentials.setInvestor(investor);
    }

    // --- Investor login ---

    @Test
    void login_investor_success() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        LoginRequest req = new LoginRequest();
        req.setUsername("jane@example.com");
        req.setPassword("secret");

        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken("jane@example.com", "INVESTOR")).thenReturn("jwt-token");

        LoginResponse response = authService.login(req, httpRequest);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRole()).isEqualTo("INVESTOR");
        assertThat(response.getFirstName()).isEqualTo("Jane");
        verify(loginLogRepo).save(argThat(log -> "SUCCESS".equals(log.getStatus())));
    }

    @Test
    void login_investor_wrong_password_throws() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        LoginRequest req = new LoginRequest();
        req.setUsername("jane@example.com");
        req.setPassword("wrong");

        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");
        verify(loginLogRepo).save(argThat(log -> "FAILED".equals(log.getStatus())));
    }

    @Test
    void login_investor_inactive_throws() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        credentials.setIsActive(false);
        LoginRequest req = new LoginRequest();
        req.setUsername("jane@example.com");
        req.setPassword("secret");

        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));

        assertThatThrownBy(() -> authService.login(req, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Account is inactive");
        verify(loginLogRepo).save(argThat(log -> "FAILED".equals(log.getStatus())));
    }

    // --- Admin login ---

    @Test
    void login_admin_success() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        AdminUser admin = new AdminUser();
        admin.setEmail("admin@example.com");
        admin.setName("Admin");
        admin.setPasswordHash("adminHash");
        admin.setIsActive(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin@example.com");
        req.setPassword("adminPass");

        when(credentialsRepo.findByUsername("admin@example.com")).thenReturn(Optional.empty());
        when(adminUserRepo.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("adminPass", "adminHash")).thenReturn(true);
        when(jwtUtil.generateToken("admin@example.com", "ADMIN")).thenReturn("admin-jwt");

        LoginResponse response = authService.login(req, httpRequest);

        assertThat(response.getToken()).isEqualTo("admin-jwt");
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getFirstName()).isEqualTo("Admin");
    }

    @Test
    void login_admin_wrong_password_throws() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        AdminUser admin = new AdminUser();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash("adminHash");
        admin.setIsActive(true);

        LoginRequest req = new LoginRequest();
        req.setUsername("admin@example.com");
        req.setPassword("wrong");

        when(credentialsRepo.findByUsername("admin@example.com")).thenReturn(Optional.empty());
        when(adminUserRepo.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong", "adminHash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void login_unknown_user_throws() {
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        LoginRequest req = new LoginRequest();
        req.setUsername("nobody@example.com");
        req.setPassword("pass");

        when(credentialsRepo.findByUsername("nobody@example.com")).thenReturn(Optional.empty());
        when(adminUserRepo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req, httpRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid credentials");
    }

    // --- Forgot password ---

    @Test
    void forgotPassword_sends_email_when_user_found() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("jane@example.com");

        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));
        when(credentialsRepo.save(any())).thenReturn(credentials);

        authService.forgotPassword(req);

        verify(emailService).sendPasswordResetEmail(eq("jane@example.com"), anyString());
        assertThat(credentials.getResetToken()).isNotNull();
        assertThat(credentials.getResetTokenExpiry()).isAfter(LocalDateTime.now());
    }

    @Test
    void forgotPassword_silently_succeeds_when_user_not_found() {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("nobody@example.com");

        when(credentialsRepo.findByUsername("nobody@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> authService.forgotPassword(req)).doesNotThrowAnyException();
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    // --- Reset password ---

    @Test
    void resetPassword_updates_hash_and_clears_token() {
        credentials.setResetToken("valid-token");
        credentials.setResetTokenExpiry(LocalDateTime.now().plusMinutes(30));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-token");
        req.setNewPassword("NewPass#123");

        when(credentialsRepo.findByResetToken("valid-token")).thenReturn(Optional.of(credentials));
        when(passwordEncoder.encode("NewPass#123")).thenReturn("newHash");

        authService.resetPassword(req);

        assertThat(credentials.getPasswordHash()).isEqualTo("newHash");
        assertThat(credentials.getResetToken()).isNull();
        assertThat(credentials.getResetTokenExpiry()).isNull();
        verify(credentialsRepo).save(credentials);
    }

    @Test
    void resetPassword_expired_token_throws() {
        credentials.setResetToken("expired-token");
        credentials.setResetTokenExpiry(LocalDateTime.now().minusMinutes(1));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("expired-token");
        req.setNewPassword("NewPass#123");

        when(credentialsRepo.findByResetToken("expired-token")).thenReturn(Optional.of(credentials));

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Token has expired");
    }

    @Test
    void resetPassword_invalid_token_throws() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("bad-token");
        req.setNewPassword("NewPass#123");

        when(credentialsRepo.findByResetToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Invalid or expired token");
    }
}
