package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.InvestorResponse;
import com.melissafieldstone.portal.dto.InvestorUpdateRequest;
import com.melissafieldstone.portal.entity.Investor;
import com.melissafieldstone.portal.entity.InvestorCredentials;
import com.melissafieldstone.portal.repository.InvestorCredentialsRepository;
import com.melissafieldstone.portal.repository.InvestorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvestorService {

    private final InvestorRepository investorRepo;
    private final InvestorCredentialsRepository credentialsRepo;

    public InvestorResponse getMyProfile(String username) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        return toResponse(cred.getInvestor());
    }

    public InvestorResponse updateMyProfile(String username, InvestorUpdateRequest request) {
        InvestorCredentials cred = credentialsRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Investor not found"));
        Investor investor = cred.getInvestor();
        if (request.getPhone() != null) investor.setPhone(request.getPhone());
        if (request.getEmailAddress2() != null) investor.setEmailAddress2(request.getEmailAddress2());
        if (request.getMailingAddress() != null) investor.setMailingAddress(request.getMailingAddress());
        if (request.getBeneficiaryName() != null) investor.setBeneficiaryName(request.getBeneficiaryName());
        if (request.getBeneficiaryPhone() != null) investor.setBeneficiaryPhone(request.getBeneficiaryPhone());
        investorRepo.save(investor);
        return toResponse(investor);
    }

    public InvestorResponse toResponse(Investor i) {
        InvestorResponse r = new InvestorResponse();
        r.setInvestorId(i.getInvestorId());
        r.setFirstName(i.getFirstName());
        r.setLastName(i.getLastName());
        r.setEmail(i.getEmail());
        r.setEmailAddress2(i.getEmailAddress2());
        r.setPhone(i.getPhone());
        r.setMailingAddress(i.getMailingAddress());
        r.setBeneficiaryName(i.getBeneficiaryName());
        r.setBeneficiaryPhone(i.getBeneficiaryPhone());
        r.setNumberOfShares(i.getNumberOfShares());
        r.setInvestmentSource(i.getInvestmentSource());
        r.setLlcName(i.getLlcName());
        r.setMajorId(i.getMajorId());
        r.setCompanyShareHolding(i.getCompanyShareHolding());
        return r;
    }
}
