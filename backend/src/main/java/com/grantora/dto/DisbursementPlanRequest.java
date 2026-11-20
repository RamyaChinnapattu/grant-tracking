package com.grantora.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DisbursementPlanRequest(
    @NotNull(message = "Application ID is required")
    Long applicationId,

    String notes,

    @NotEmpty(message = "At least one installment stage is required")
    List<InstallmentRequest> installments
) {}
