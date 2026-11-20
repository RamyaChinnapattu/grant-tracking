package com.grantora.dto;

import com.grantora.entity.DisbursementPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DisbursementPlanResponse(
    Long id,
    Long applicationId,
    Long schemeId,
    String schemeName,
    String schemeDepartment,
    Long beneficiaryId,
    String beneficiaryName,
    String beneficiaryState,
    String beneficiaryDistrict,
    BigDecimal totalApprovedAmount,
    BigDecimal totalDisbursedAmount,
    BigDecimal totalScheduledAmount,
    BigDecimal remainingAmount,
    DisbursementPlanStatus planStatus,
    String notes,
    String createdByName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<DisbursementInstallmentResponse> installments
) {}
