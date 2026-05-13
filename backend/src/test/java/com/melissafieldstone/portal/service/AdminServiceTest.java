package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.CreateInvestorRequest;
import com.melissafieldstone.portal.dto.InvestorResponse;
import com.melissafieldstone.portal.dto.InvestorUpdateRequest;
import com.melissafieldstone.portal.dto.LoginLogResponse;
import com.melissafieldstone.portal.entity.Investor;
import com.melissafieldstone.portal.entity.InvestorCredentials;
import com.melissafieldstone.portal.entity.InvestorLoginLog;
import com.melissafieldstone.portal.repository.InvestorCredentialsRepository;
import com.melissafieldstone.portal.repository.InvestorLoginLogRepository;
import com.melissafieldstone.portal.repository.InvestorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock InvestorRepository investorRepo;
    @Mock InvestorCredentialsRepository credentialsRepo;
    @Mock InvestorLoginLogRepository loginLogRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock InvestorService investorService;

    @InjectMocks AdminService adminService;

    private Investor investor;
    private InvestorCredentials credentials;

    @BeforeEach
    void setUp() {
        investor = new Investor();
        investor.setInvestorId(1);
        investor.setFirstName("Jane");
        investor.setLastName("Doe");
        investor.setEmail("jane@example.com");
        investor.setPhone("1234567890");

        credentials = new InvestorCredentials();
        credentials.setUsername("jane@example.com");
        credentials.setPasswordHash("hashed");
        credentials.setRole("INVESTOR");
        credentials.setIsActive(true);
        credentials.setInvestor(investor);
    }

    // --- getAllInvestors ---

    @Test
    void getAllInvestors_returns_mapped_list() {
        InvestorResponse response = new InvestorResponse();
        response.setInvestorId(1);
        response.setFirstName("Jane");

        when(investorRepo.findAll()).thenReturn(List.of(investor));
        when(investorService.toResponse(investor)).thenReturn(response);

        List<InvestorResponse> result = adminService.getAllInvestors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Jane");
    }

    @Test
    void getAllInvestors_empty_list() {
        when(investorRepo.findAll()).thenReturn(List.of());

        List<InvestorResponse> result = adminService.getAllInvestors();

        assertThat(result).isEmpty();
    }

    // --- getInvestorById ---

    @Test
    void getInvestorById_returns_response() {
        InvestorResponse response = new InvestorResponse();
        response.setInvestorId(1);

        when(investorRepo.findById(1)).thenReturn(Optional.of(investor));
        when(investorService.toResponse(investor)).thenReturn(response);

        InvestorResponse result = adminService.getInvestorById(1);

        assertThat(result.getInvestorId()).isEqualTo(1);
    }

    @Test
    void getInvestorById_not_found_throws() {
        when(investorRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getInvestorById(99))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Investor not found");
    }

    // --- createInvestor ---

    @Test
    void createInvestor_persists_investor_and_credentials() {
        CreateInvestorRequest req = new CreateInvestorRequest();
        req.setFirstName("New");
        req.setLastName("User");
        req.setEmail("new@example.com");
        req.setPassword("Pass#1234");
        req.setPhone("5555555555");
        req.setNumberOfShares(50);
        req.setLlcName("NewLLC");
        req.setCompanyShareHolding(new BigDecimal("0.0500"));

        InvestorResponse expectedResponse = new InvestorResponse();
        expectedResponse.setFirstName("New");

        when(investorRepo.save(any(Investor.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("Pass#1234")).thenReturn("hashedNew");
        when(credentialsRepo.save(any(InvestorCredentials.class))).thenAnswer(inv -> inv.getArgument(0));
        when(investorService.toResponse(any(Investor.class))).thenReturn(expectedResponse);

        InvestorResponse result = adminService.createInvestor(req);

        assertThat(result.getFirstName()).isEqualTo("New");
        verify(investorRepo).save(argThat(i -> "New".equals(i.getFirstName()) && "new@example.com".equals(i.getEmail())));
        verify(credentialsRepo).save(argThat(c ->
                "new@example.com".equals(c.getUsername()) &&
                "hashedNew".equals(c.getPasswordHash()) &&
                "INVESTOR".equals(c.getRole()) &&
                Boolean.TRUE.equals(c.getIsActive())
        ));
    }

    // --- updateInvestor ---

    @Test
    void updateInvestor_updates_phone() {
        InvestorUpdateRequest req = new InvestorUpdateRequest();
        req.setPhone("9999999999");

        InvestorResponse expected = new InvestorResponse();
        expected.setPhone("9999999999");

        when(investorRepo.findById(1)).thenReturn(Optional.of(investor));
        when(investorRepo.save(investor)).thenReturn(investor);
        when(investorService.toResponse(investor)).thenReturn(expected);

        InvestorResponse result = adminService.updateInvestor(1, req);

        assertThat(result.getPhone()).isEqualTo("9999999999");
        assertThat(investor.getPhone()).isEqualTo("9999999999");
    }

    @Test
    void updateInvestor_not_found_throws() {
        when(investorRepo.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateInvestor(99, new InvestorUpdateRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Investor not found");
    }

    @Test
    void updateInvestor_null_fields_not_overwritten() {
        investor.setPhone("1234567890");
        InvestorUpdateRequest req = new InvestorUpdateRequest();

        when(investorRepo.findById(1)).thenReturn(Optional.of(investor));
        when(investorRepo.save(investor)).thenReturn(investor);
        when(investorService.toResponse(investor)).thenReturn(new InvestorResponse());

        adminService.updateInvestor(1, req);

        assertThat(investor.getPhone()).isEqualTo("1234567890");
    }

    // --- deactivateInvestor ---

    @Test
    void deactivateInvestor_sets_inactive() {
        when(credentialsRepo.findAll()).thenReturn(List.of(credentials));

        adminService.deactivateInvestor(1);

        assertThat(credentials.getIsActive()).isFalse();
        verify(credentialsRepo).save(credentials);
    }

    @Test
    void deactivateInvestor_not_found_throws() {
        when(credentialsRepo.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> adminService.deactivateInvestor(99))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Investor credentials not found");
    }

    // --- getLoginLogs ---

    @Test
    void getLoginLogs_returns_mapped_logs() {
        InvestorLoginLog log = new InvestorLoginLog();
        log.setLogId(10);
        log.setLoginTimestamp(LocalDateTime.of(2025, 1, 15, 10, 0));
        log.setIpAddress("192.168.1.1");
        log.setStatus("SUCCESS");
        log.setInvestor(investor);

        when(loginLogRepo.findAllByOrderByLoginTimestampDesc()).thenReturn(List.of(log));

        List<LoginLogResponse> result = adminService.getLoginLogs();

        assertThat(result).hasSize(1);
        LoginLogResponse r = result.get(0);
        assertThat(r.getLogId()).isEqualTo(10);
        assertThat(r.getStatus()).isEqualTo("SUCCESS");
        assertThat(r.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(r.getInvestorId()).isEqualTo(1);
        assertThat(r.getInvestorName()).isEqualTo("Jane Doe");
    }

    @Test
    void getLoginLogs_no_investor_does_not_set_investor_fields() {
        InvestorLoginLog log = new InvestorLoginLog();
        log.setLogId(11);
        log.setLoginTimestamp(LocalDateTime.now());
        log.setStatus("FAILED");
        log.setInvestor(null);

        when(loginLogRepo.findAllByOrderByLoginTimestampDesc()).thenReturn(List.of(log));

        List<LoginLogResponse> result = adminService.getLoginLogs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getInvestorId()).isNull();
        assertThat(result.get(0).getInvestorName()).isNull();
    }
}
