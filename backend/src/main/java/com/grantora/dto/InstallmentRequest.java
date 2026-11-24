package com.grantora.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InstallmentRequest(
    @NotNull(message = "Installment number is required")
    Integer installmentNumber,

    @NotBlank(message = "Installment title is required")
    String title,

    BigDecimal percentage,

    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    BigDecimal amount,

    LocalDate plannedReleaseDate,

    List<MilestoneRequest> milestones
) {}
