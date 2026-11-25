package com.grantora.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ApplicationDisbursementSummary(
        String disbursementStatus,
        Long planId,
        String planStatus,
        BigDecimal totalApprovedAmount,
        BigDecimal disbursedAmount,
        BigDecimal remainingAmount,
        int releasedInstallmentsCount,
        int totalInstallmentsCount,
        List<InstallmentSummary> installments
) {
    public record InstallmentSummary(
            Long id,
            Integer installmentNumber,
            String title,
            BigDecimal amount,
            String status,
            LocalDate plannedReleaseDate,
            LocalDateTime actualDisbursementDate,
            String paymentReference
    ) {}
}
