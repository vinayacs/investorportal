package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.InvestorResponse;
import com.melissafieldstone.portal.dto.InvestorUpdateRequest;
import com.melissafieldstone.portal.entity.Investor;
import com.melissafieldstone.portal.entity.InvestorCredentials;
import com.melissafieldstone.portal.repository.InvestorCredentialsRepository;
import com.melissafieldstone.portal.repository.InvestorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestorServiceTest {

    @Mock InvestorRepository investorRepo;
    @Mock InvestorCredentialsRepository credentialsRepo;

    @InjectMocks InvestorService investorService;

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
        investor.setMailingAddress("123 Main St");
        investor.setBeneficiaryName("John Doe");
        investor.setBeneficiaryPhone("0987654321");
        investor.setNumberOfShares(100);
        investor.setInvestmentSource("Cash");
        investor.setLlcName("DoeLLC");
        investor.setMajorId("M001");
        investor.setCompanyShareHolding(new BigDecimal("0.1000"));

        credentials = new InvestorCredentials();
        credentials.setUsername("jane@example.com");
        credentials.setInvestor(investor);
    }

    // --- getMyProfile ---

    @Test
    void getMyProfile_returns_investor_response() {
        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));

        InvestorResponse response = investorService.getMyProfile("jane@example.com");

        assertThat(response.getInvestorId()).isEqualTo(1);
        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getPhone()).isEqualTo("1234567890");
    }

    @Test
    void getMyProfile_unknown_username_throws() {
        when(credentialsRepo.findByUsername("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investorService.getMyProfile("nobody@example.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Investor not found");
    }

    // --- updateMyProfile ---

    @Test
    void updateMyProfile_updates_phone() {
        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));
        when(investorRepo.save(investor)).thenReturn(investor);

        InvestorUpdateRequest req = new InvestorUpdateRequest();
        req.setPhone("9999999999");

        InvestorResponse response = investorService.updateMyProfile("jane@example.com", req);

        assertThat(response.getPhone()).isEqualTo("9999999999");
        verify(investorRepo).save(investor);
    }

    @Test
    void updateMyProfile_updates_all_editable_fields() {
        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));
        when(investorRepo.save(investor)).thenReturn(investor);

        InvestorUpdateRequest req = new InvestorUpdateRequest();
        req.setPhone("5555555555");
        req.setEmailAddress2("jane2@example.com");
        req.setMailingAddress("456 New St");
        req.setBeneficiaryName("Mary Doe");
        req.setBeneficiaryPhone("1111111111");

        InvestorResponse response = investorService.updateMyProfile("jane@example.com", req);

        assertThat(response.getPhone()).isEqualTo("5555555555");
        assertThat(response.getEmailAddress2()).isEqualTo("jane2@example.com");
        assertThat(response.getMailingAddress()).isEqualTo("456 New St");
        assertThat(response.getBeneficiaryName()).isEqualTo("Mary Doe");
        assertThat(response.getBeneficiaryPhone()).isEqualTo("1111111111");
    }

    @Test
    void updateMyProfile_null_fields_are_not_overwritten() {
        when(credentialsRepo.findByUsername("jane@example.com")).thenReturn(Optional.of(credentials));
        when(investorRepo.save(investor)).thenReturn(investor);

        InvestorUpdateRequest req = new InvestorUpdateRequest();
        // All fields null — nothing should change

        InvestorResponse response = investorService.updateMyProfile("jane@example.com", req);

        assertThat(response.getPhone()).isEqualTo("1234567890");
        assertThat(response.getMailingAddress()).isEqualTo("123 Main St");
    }

    @Test
    void updateMyProfile_unknown_username_throws() {
        when(credentialsRepo.findByUsername("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> investorService.updateMyProfile("nobody@example.com", new InvestorUpdateRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Investor not found");
    }

    // --- toResponse ---

    @Test
    void toResponse_maps_all_fields() {
        InvestorResponse response = investorService.toResponse(investor);

        assertThat(response.getInvestorId()).isEqualTo(1);
        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getEmail()).isEqualTo("jane@example.com");
        assertThat(response.getPhone()).isEqualTo("1234567890");
        assertThat(response.getMailingAddress()).isEqualTo("123 Main St");
        assertThat(response.getBeneficiaryName()).isEqualTo("John Doe");
        assertThat(response.getBeneficiaryPhone()).isEqualTo("0987654321");
        assertThat(response.getNumberOfShares()).isEqualTo(100);
        assertThat(response.getInvestmentSource()).isEqualTo("Cash");
        assertThat(response.getLlcName()).isEqualTo("DoeLLC");
        assertThat(response.getMajorId()).isEqualTo("M001");
        assertThat(response.getCompanyShareHolding()).isEqualByComparingTo(new BigDecimal("0.1000"));
    }
}
