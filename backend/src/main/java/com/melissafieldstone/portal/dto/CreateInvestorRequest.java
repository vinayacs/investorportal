package com.melissafieldstone.portal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateInvestorRequest {
    @NotBlank
    private String firstName;
    private String lastName;
    @NotBlank
    @Email
    private String email;
    @NotBlank
    @Size(min = 8)
    private String password;
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
