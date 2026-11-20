package com.grantora.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MilestoneRequest(
    @NotBlank(message = "Milestone title is required")
    String title,

    String description,

    LocalDate dueDate,

    BigDecimal amount,

    List<String> requirementTitles
) {}
