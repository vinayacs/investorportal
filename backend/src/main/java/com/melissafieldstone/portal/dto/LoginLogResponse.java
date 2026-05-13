package com.melissafieldstone.portal.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class LoginLogResponse {
    private Integer logId;
    private Integer investorId;
    private String investorName;
    private LocalDateTime loginTimestamp;
    private String ipAddress;
    private String status;
    private String failureReason;
    private String action;
}
