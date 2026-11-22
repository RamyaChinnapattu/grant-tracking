package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_reminder_history", uniqueConstraints = @UniqueConstraint(name = "uk_compliance_reminder_event", columnNames = {"compliance_id", "event_type", "event_date"}))
public class ComplianceReminderHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compliance_id", nullable = false) private MilestoneCompliance compliance;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    @Column(nullable = false) private LocalDateTime sentAt = LocalDateTime.now();
    public Long getId(){return id;} public MilestoneCompliance getCompliance(){return compliance;} public void setCompliance(MilestoneCompliance v){compliance=v;}
    public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;} public LocalDate getEventDate(){return eventDate;} public void setEventDate(LocalDate v){eventDate=v;} public LocalDateTime getSentAt(){return sentAt;}
}
