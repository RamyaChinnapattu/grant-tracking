package com.grantora.dto;

import com.grantora.entity.MilestoneStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MilestoneProgressUpdateRequest(
    @Min(value = 0, message = "Progress must be at least 0%")
    @Max(value = 100, message = "Progress cannot exceed 100%")
    Integer progressPercentage,

    MilestoneStatus status,

    String remarks
) {}
