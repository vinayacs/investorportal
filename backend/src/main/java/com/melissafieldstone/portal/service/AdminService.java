package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.entity.*;
import com.melissafieldstone.portal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final InvestorRepository investorRepo;
    private final InvestorCredentialsRepository credentialsRepo;
    private final InvestorLoginLogRepository loginLogRepo;
    private final PasswordEncoder passwordEncoder;
    private final InvestorService investorService;

    public List<InvestorResponse> getAllInvestors() {
        return investorRepo.findAll().stream().map(investorService::toResponse).toList();
    }

    public InvestorResponse getInvestorById(Integer id) {
        Investor investor = investorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return investorService.toResponse(investor);
    }

    public InvestorResponse createInvestor(CreateInvestorRequest request) {
        Investor investor = new Investor();
        investor.setFirstName(request.getFirstName());
        investor.setLastName(request.getLastName());
        investor.setEmail(request.getEmail());
        investor.setEmailAddress2(request.getEmailAddress2());
        investor.setPhone(request.getPhone());
        investor.setMailingAddress(request.getMailingAddress());
        investor.setBeneficiaryName(request.getBeneficiaryName());
        investor.setBeneficiaryPhone(request.getBeneficiaryPhone());
        investor.setNumberOfShares(request.getNumberOfShares());
        investor.setInvestmentSource(request.getInvestmentSource());
        investor.setLlcName(request.getLlcName());
        investor.setMajorId(request.getMajorId());
        investor.setCompanyShareHolding(request.getCompanyShareHolding());
        investorRepo.save(investor);

        InvestorCredentials cred = new InvestorCredentials();
        cred.setInvestor(investor);
        cred.setUsername(request.getEmail());
        cred.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        cred.setRole("INVESTOR");
        cred.setIsActive(true);
        credentialsRepo.save(cred);

        return investorService.toResponse(investor);
    }

    public InvestorResponse updateInvestor(Integer id, InvestorUpdateRequest request) {
        Investor investor = investorRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        if (request.getPhone() != null) investor.setPhone(request.getPhone());
        if (request.getEmailAddress2() != null) investor.setEmailAddress2(request.getEmailAddress2());
        if (request.getMailingAddress() != null) investor.setMailingAddress(request.getMailingAddress());
        if (request.getBeneficiaryName() != null) investor.setBeneficiaryName(request.getBeneficiaryName());
        if (request.getBeneficiaryPhone() != null) investor.setBeneficiaryPhone(request.getBeneficiaryPhone());
        investorRepo.save(investor);
        return investorService.toResponse(investor);
    }

    public void deactivateInvestor(Integer id) {
        InvestorCredentials cred = credentialsRepo.findAll().stream()
                .filter(c -> c.getInvestor().getInvestorId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Investor credentials not found"));
        cred.setIsActive(false);
        credentialsRepo.save(cred);
    }

    public List<LoginLogResponse> getLoginLogs() {
        return loginLogRepo.findAllByOrderByLoginTimestampDesc().stream().map(log -> {
            LoginLogResponse r = new LoginLogResponse();
            r.setLogId(log.getLogId());
            r.setLoginTimestamp(log.getLoginTimestamp());
            r.setIpAddress(log.getIpAddress());
            r.setStatus(log.getStatus());
            r.setFailureReason(log.getFailureReason());
            r.setAction(log.getAction());
            if (log.getInvestor() != null) {
                r.setInvestorId(log.getInvestor().getInvestorId());
                r.setInvestorName(log.getInvestor().getFirstName() + " " + log.getInvestor().getLastName());
            }
            return r;
        }).toList();
    }
}
