package com.grantora.dto;

import java.math.BigDecimal;
import java.util.List;

public record FundUtilizationSummaryResponse(
    BigDecimal totalAllocatedFunds,
    BigDecimal totalApprovedAmount,
    BigDecimal totalScheduledAmount,
    BigDecimal totalDisbursedAmount,
    BigDecimal remainingAllocatedAmount,
    BigDecimal remainingApprovedAmount,
    BigDecimal overallUtilizationRate,
    List<SchemeFundUtilizationResponse> schemes,
    List<InstallmentStatusBreakdownResponse> installmentStatuses
) {}
