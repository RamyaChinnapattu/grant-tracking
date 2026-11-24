package com.grantora.dto;
import java.math.BigDecimal;
public record FundAnalyticsStatusBreakdown(String status,long installmentCount,BigDecimal amount) {}
