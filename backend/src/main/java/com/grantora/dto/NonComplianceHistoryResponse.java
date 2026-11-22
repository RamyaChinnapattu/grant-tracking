package com.grantora.dto;
import com.grantora.entity.NonComplianceStatus;
import java.time.LocalDateTime;
public record NonComplianceHistoryResponse(NonComplianceStatus status,String actorName,String remarks,LocalDateTime occurredAt) {}
