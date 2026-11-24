package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "disbursement_reminder_configs")
public class DisbursementReminderConfig {

    @Id
    private Long id = 1L;

    @Column(name = "upcoming_days_before", nullable = false)
    private Integer upcomingDaysBefore = 3;

    @Column(name = "auto_flag_overdue", nullable = false)
    private Boolean autoFlagOverdue = true;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public DisbursementReminderConfig() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getUpcomingDaysBefore() { return upcomingDaysBefore; }
    public void setUpcomingDaysBefore(Integer upcomingDaysBefore) { this.upcomingDaysBefore = upcomingDaysBefore; }

    public Boolean getAutoFlagOverdue() { return autoFlagOverdue; }
    public void setAutoFlagOverdue(Boolean autoFlagOverdue) { this.autoFlagOverdue = autoFlagOverdue; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
