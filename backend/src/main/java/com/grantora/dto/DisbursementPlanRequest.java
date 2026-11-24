package com.grantora.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;

public record DisbursementPlanRequest(
    @NotNull(message = "Application ID is required")
    Long applicationId,

    String notes,

    @NotEmpty(message = "At least one installment stage is required")
    List<@Valid InstallmentRequest> installments
) {}
