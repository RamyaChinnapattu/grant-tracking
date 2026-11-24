package com.grantora.dto;

import com.grantora.entity.MilestoneStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DisbursementMilestoneResponse(
    Long id,
    Long installmentId,
    Integer installmentNumber,
    String installmentTitle,
    Long planId,
    Long applicationId,
    String schemeName,
    String beneficiaryName,
    String title,
    String description,
    LocalDate dueDate,
    BigDecimal amount,
    MilestoneStatus status,
    Integer progressPercentage,
    boolean nonCompliant,
    String nonComplianceReason,
    LocalDateTime completedAt,
    List<MilestoneComplianceResponse> compliances
) {}
