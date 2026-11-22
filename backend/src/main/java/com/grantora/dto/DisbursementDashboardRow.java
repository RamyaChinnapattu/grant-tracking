package com.grantora.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
public record DisbursementDashboardRow(Long applicationId,String beneficiaryName,String schemeName,String state,String district,
        String applicationStatus,String disbursementStatus,BigDecimal approvedAmount,BigDecimal releasedAmount,
        Integer nextInstallmentNumber,BigDecimal nextInstallmentAmount,LocalDate nextInstallmentDate,String complianceStatus) {}
