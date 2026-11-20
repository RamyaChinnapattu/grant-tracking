package com.grantora.dto;

import com.grantora.entity.InstallmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DisbursementInstallmentResponse(
    Long id,
    Long planId,
    Integer installmentNumber,
    String title,
    BigDecimal percentage,
    BigDecimal amount,
    LocalDate plannedReleaseDate,
    LocalDateTime actualDisbursementDate,
    InstallmentStatus status,
    String paymentReference,
    String paymentMethod,
    String disbursedByName,
    String disbursementRemarks,
    List<DisbursementMilestoneResponse> milestones,
    boolean allCompliancesVerified
) {}
