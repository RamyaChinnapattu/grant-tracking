package com.grantora.dto;
import com.grantora.entity.GrantUtilizationStatus;
import java.time.LocalDateTime;
public record UtilizationHistoryResponse(GrantUtilizationStatus status,String actorName,String remarks,LocalDateTime occurredAt) {}
