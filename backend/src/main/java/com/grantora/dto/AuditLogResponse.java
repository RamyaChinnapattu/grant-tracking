package com.grantora.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        String id,
        String category,
        String action,
        Long applicationId,
        String schemeName,
        String beneficiaryName,
        String actorName,
        String actorEmail,
        String actorRole,
        String fromStatus,
        String toStatus,
        String targetEntity,
        Long targetId,
        String remarks,
        LocalDateTime timestamp
) {
}
