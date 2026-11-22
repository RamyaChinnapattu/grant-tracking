package com.grantora.dto;
import java.math.BigDecimal;
public record SchemeFundAnalyticsResponse(Long schemeId,String schemeName,String department,BigDecimal allocatedFunds,
        BigDecimal approvedAmount,BigDecimal scheduledAmount,BigDecimal releasedAmount,BigDecimal reportedExpenditure,
        BigDecimal verifiedExpenditure,BigDecimal pendingForRelease,BigDecimal remainingReportedBalance,long activePlansCount) {}
