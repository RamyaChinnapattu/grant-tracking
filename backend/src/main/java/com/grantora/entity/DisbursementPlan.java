package com.grantora.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "disbursement_plans", uniqueConstraints = {
    @UniqueConstraint(name = "uk_disbursement_plan_application", columnNames = {"application_id"})
})
public class DisbursementPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private GrantApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "total_approved_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalApprovedAmount;

    @Column(name = "total_disbursed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDisbursedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", nullable = false, length = 30)
    private DisbursementPlanStatus planStatus = DisbursementPlanStatus.ACTIVE;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<DisbursementInstallment> installments = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<DisbursementPlanAudit> auditHistory = new ArrayList<>();

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (totalDisbursedAmount == null) totalDisbursedAmount = BigDecimal.ZERO;
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public DisbursementPlan() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public GrantApplication getApplication() { return application; }
    public void setApplication(GrantApplication application) { this.application = application; }

    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }

    public BigDecimal getTotalApprovedAmount() { return totalApprovedAmount; }
    public void setTotalApprovedAmount(BigDecimal totalApprovedAmount) { this.totalApprovedAmount = totalApprovedAmount; }

    public BigDecimal getTotalDisbursedAmount() { return totalDisbursedAmount; }
    public void setTotalDisbursedAmount(BigDecimal totalDisbursedAmount) { this.totalDisbursedAmount = totalDisbursedAmount; }

    public DisbursementPlanStatus getPlanStatus() { return planStatus; }
    public void setPlanStatus(DisbursementPlanStatus planStatus) { this.planStatus = planStatus; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<DisbursementInstallment> getInstallments() { return installments; }
    public void setInstallments(List<DisbursementInstallment> installments) {
        this.installments.clear();
        if (installments != null) {
            installments.forEach(inst -> {
                inst.setPlan(this);
                this.installments.add(inst);
            });
        }
    }

    public void addInstallment(DisbursementInstallment installment) {
        installment.setPlan(this);
        this.installments.add(installment);
    }

    public void addAuditEntry(DisbursementPlanAudit entry) {
        auditHistory.add(entry);
    }

    public List<DisbursementPlanAudit> getAuditHistory() {
        return auditHistory;
    }

    public void setAuditHistory(List<DisbursementPlanAudit> auditHistory) {
        this.auditHistory = auditHistory;
    }
}
