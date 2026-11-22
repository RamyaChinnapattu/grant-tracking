package com.grantora.dto;
import java.math.BigDecimal;
public record RegionalFundAnalyticsResponse(String state,String district,String taluk,Long schemeId,String schemeName,
        String applicationStatus,String disbursementStatus,long applicationCount,long approvedApplicationCount,
        BigDecimal approvedAmount,BigDecimal scheduledAmount,BigDecimal releasedAmount,BigDecimal reportedExpenditure,
        BigDecimal verifiedExpenditure,BigDecimal pendingForRelease) {}
