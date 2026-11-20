package com.grantora.dto;

import java.math.BigDecimal;

public record RegionalAnalyticsResponse(
    String state,
    String district,
    long totalApplications,
    long approvedApplications,
    BigDecimal totalApprovedAmount,
    BigDecimal totalDisbursedAmount,
    BigDecimal pendingDisbursementAmount,
    long verifiedMilestonesCount,
    long totalMilestonesCount,
    BigDecimal complianceRate,
    BigDecimal fundUtilizationRate
) {}
