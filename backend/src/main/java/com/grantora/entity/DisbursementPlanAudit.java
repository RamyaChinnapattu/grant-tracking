package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "disbursement_plan_audit")
public class DisbursementPlanAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private DisbursementPlan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false)
    private LocalDateTime occurredAt = LocalDateTime.now();

    public DisbursementPlanAudit() {}
    public DisbursementPlanAudit(DisbursementPlan plan, User actor, String action, String details) {
        this.plan = plan; this.actor = actor; this.action = action; this.details = details;
    }
    public Long getId() { return id; }
    public DisbursementPlan getPlan() { return plan; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
