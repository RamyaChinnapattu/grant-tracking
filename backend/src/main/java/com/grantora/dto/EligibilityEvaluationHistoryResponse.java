package com.grantora.dto;

import java.time.LocalDateTime;

public record EligibilityEvaluationHistoryResponse(
        int configurationVersion,
        EligibilityEvaluationResult evaluation,
        LocalDateTime evaluatedAt) {}
