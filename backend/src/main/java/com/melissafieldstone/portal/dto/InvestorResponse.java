package com.melissafieldstone.portal.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InvestorResponse {
    private Integer investorId;
    private String firstName;
    private String lastName;
    private String email;
    private String emailAddress2;
    private String phone;
    private String mailingAddress;
    private String beneficiaryName;
    private String beneficiaryPhone;
    private Integer numberOfShares;
    private String investmentSource;
    private String llcName;
    private String majorId;
    private BigDecimal companyShareHolding;
}
