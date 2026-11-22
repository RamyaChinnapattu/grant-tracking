package com.grantora.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public record FundAnalyticsResponse(LocalDateTime generatedAt,BigDecimal totalAllocatedFunds,BigDecimal totalApprovedAmount,
        BigDecimal totalScheduledAmount,BigDecimal totalReleasedAmount,BigDecimal totalReportedExpenditure,
        BigDecimal totalVerifiedExpenditure,BigDecimal remainingReportedGrantBalance,BigDecimal pendingForRelease,
        BigDecimal amountUnderReview,List<SchemeFundAnalyticsResponse> schemes,
        List<RegionalFundAnalyticsResponse> regions,List<FundAnalyticsStatusBreakdown> disbursementStatuses) {}
