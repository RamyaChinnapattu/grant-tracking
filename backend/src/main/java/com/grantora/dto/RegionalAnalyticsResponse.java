package com.grantora.dto;

import java.math.BigDecimal;

public record RegionalAnalyticsResponse(
        String state,
        String district,
        long totalApplications,
        long approvedApplications,
        long rejectedApplications,
        BigDecimal totalApprovedAmount,
        BigDecimal totalDisbursedAmount,
        BigDecimal pendingDisbursementAmount,
        long verifiedMilestonesCount,
        long totalMilestonesCount,
        BigDecimal complianceRate,
        BigDecimal fundUtilizationRate) {
    public RegionalAnalyticsResponse(
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
    ) {
        this(state, district, totalApplications, approvedApplications, 0L, totalApprovedAmount,
             totalDisbursedAmount, pendingDisbursementAmount, verifiedMilestonesCount, totalMilestonesCount,
             complianceRate, fundUtilizationRate);
    }
}
