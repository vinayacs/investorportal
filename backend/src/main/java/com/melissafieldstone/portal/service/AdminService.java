package com.melissafieldstone.portal.service;

import com.melissafieldstone.portal.dto.*;
import com.melissafieldstone.portal.entity.*;
import com.melissafieldstone.portal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final InvestorRepository investorRepo;
    private final InvestorCredentialsRepository credentialsRepo;
    private final InvestorLoginLogRepository loginLogRepo;
    private final PageVisitRepository pageVisitRepo;
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
            r.setCity(log.getCity());
            r.setCountry(log.getCountry());
            r.setRegion(log.getRegion());
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

    public LocationStatsResponse getLocationStats() {
        List<PageVisit> visits = pageVisitRepo.findAll();
        List<InvestorLoginLog> logins = loginLogRepo.findAll();

        Map<String, Long> countryCount = new LinkedHashMap<>();
        Map<String, Long> cityCount = new LinkedHashMap<>();
        Map<String, Long> pageCount = new LinkedHashMap<>();

        for (PageVisit v : visits) {
            if (v.getCountry() != null && !v.getCountry().isBlank())
                countryCount.merge(v.getCountry(), 1L, Long::sum);
            if (v.getCity() != null && !v.getCity().isBlank()) {
                StringBuilder key = new StringBuilder(v.getCity());
                if (v.getRegion() != null && !v.getRegion().isBlank()) key.append(", ").append(v.getRegion());
                if (v.getCountry() != null && !v.getCountry().isBlank()) key.append(", ").append(v.getCountry());
                cityCount.merge(key.toString(), 1L, Long::sum);
            }
            if (v.getPage() != null) pageCount.merge(v.getPage(), 1L, Long::sum);
        }
        for (InvestorLoginLog log : logins) {
            if (log.getCountry() != null && !log.getCountry().isBlank())
                countryCount.merge(log.getCountry(), 1L, Long::sum);
            if (log.getCity() != null && !log.getCity().isBlank()) {
                StringBuilder key = new StringBuilder(log.getCity());
                if (log.getRegion() != null && !log.getRegion().isBlank()) key.append(", ").append(log.getRegion());
                if (log.getCountry() != null && !log.getCountry().isBlank()) key.append(", ").append(log.getCountry());
                cityCount.merge(key.toString(), 1L, Long::sum);
            }
        }

        List<LocationStatsResponse.NameCount> topCountries = countryCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new LocationStatsResponse.NameCount(e.getKey(), e.getValue()))
                .toList();

        List<LocationStatsResponse.NameCount> topCities = cityCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new LocationStatsResponse.NameCount(e.getKey(), e.getValue()))
                .toList();

        List<LocationStatsResponse.NameCount> pageBreakdown = pageCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new LocationStatsResponse.NameCount(e.getKey(), e.getValue()))
                .toList();

        return new LocationStatsResponse(
                visits.size(), logins.size(),
                countryCount.size(), cityCount.size(),
                topCountries, topCities, pageBreakdown
        );
    }

    public List<PageVisitResponse> getVisitorLogs() {
        return pageVisitRepo.findTop200ByOrderByVisitedAtDesc().stream().map(v -> {
            PageVisitResponse r = new PageVisitResponse();
            r.setId(v.getId());
            r.setPage(v.getPage());
            r.setIpAddress(v.getIpAddress());
            r.setCity(v.getCity());
            r.setCountry(v.getCountry());
            r.setRegion(v.getRegion());
            r.setUserAgent(v.getUserAgent());
            r.setVisitedAt(v.getVisitedAt());
            return r;
        }).toList();
    }
}
