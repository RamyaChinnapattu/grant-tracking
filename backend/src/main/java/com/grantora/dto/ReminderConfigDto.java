package com.grantora.dto;

public record ReminderConfigDto(
    Integer upcomingDaysBefore,
    Boolean autoFlagOverdue,
    Boolean enabled
) {}
