package com.grantora.dto;

import java.math.BigDecimal;

public record SchemeFundUtilizationResponse(
    Long schemeId,
    String schemeName,
    String department,
    BigDecimal allocatedFunds,
    BigDecimal approvedAmount,
    BigDecimal scheduledAmount,
    BigDecimal disbursedAmount,
    BigDecimal remainingAmount,
    BigDecimal utilizationPercentage,
    long activePlansCount
) {}
