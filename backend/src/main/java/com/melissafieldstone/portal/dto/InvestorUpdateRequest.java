package com.melissafieldstone.portal.dto;

import lombok.Data;

@Data
public class InvestorUpdateRequest {
    private String phone;
    private String emailAddress2;
    private String mailingAddress;
    private String beneficiaryName;
    private String beneficiaryPhone;
}
