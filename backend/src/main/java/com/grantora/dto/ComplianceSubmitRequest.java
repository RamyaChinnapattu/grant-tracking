package com.grantora.dto;

public record ComplianceSubmitRequest(
    String requirementTitle,
    String description,
    String submissionNotes
) {}
