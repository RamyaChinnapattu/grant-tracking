package com.grantora.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "disbursement_milestones")
public class DisbursementMilestone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false)
    private DisbursementInstallment installment;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private MilestoneStatus status = MilestoneStatus.PLANNED;

    @Column(name = "progress_percentage", nullable = false)
    private Integer progressPercentage = 0;

    @Column(name = "non_compliant", nullable = false)
    private Boolean nonCompliant = false;

    @Column(name = "non_compliance_reason", length = 255)
    private String nonComplianceReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "milestone", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MilestoneCompliance> compliances = new ArrayList<>();

    public DisbursementMilestone() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DisbursementInstallment getInstallment() { return installment; }
    public void setInstallment(DisbursementInstallment installment) { this.installment = installment; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public MilestoneStatus getStatus() { return status; }
    public void setStatus(MilestoneStatus status) { this.status = status; }

    public Integer getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(Integer progressPercentage) { this.progressPercentage = progressPercentage; }

    public Boolean getNonCompliant() { return nonCompliant != null && nonCompliant; }
    public void setNonCompliant(Boolean nonCompliant) { this.nonCompliant = nonCompliant; }

    public String getNonComplianceReason() { return nonComplianceReason; }
    public void setNonComplianceReason(String nonComplianceReason) { this.nonComplianceReason = nonComplianceReason; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public List<MilestoneCompliance> getCompliances() { return compliances; }
    public void setCompliances(List<MilestoneCompliance> compliances) {
        this.compliances.clear();
        if (compliances != null) {
            compliances.forEach(c -> {
                c.setMilestone(this);
                this.compliances.add(c);
            });
        }
    }

    public void addCompliance(MilestoneCompliance compliance) {
        compliance.setMilestone(this);
        this.compliances.add(compliance);
    }
}
