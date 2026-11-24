package com.grantora.dto;

import java.math.BigDecimal;

public record InstallmentStatusBreakdownResponse(
    String status,
    long count,
    BigDecimal totalAmount
) {}
