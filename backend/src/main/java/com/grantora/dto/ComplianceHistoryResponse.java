package com.grantora.dto;
import java.time.LocalDateTime;
public record ComplianceHistoryResponse(String action, String actorName, String remarks, String documentFileName, LocalDateTime occurredAt) {}
