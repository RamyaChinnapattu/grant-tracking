package com.grantora.dto;

import java.time.LocalDateTime;

public record ComplianceDocumentVersionResponse(
    Integer versionNumber,
    String documentFileName,
    String documentContentType,
    Long documentFileSize,
    String submissionNotes,
    String uploadedByName,
    LocalDateTime uploadedAt
) {}
