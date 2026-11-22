package com.grantora.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "disbursement_installments", uniqueConstraints = {
    @UniqueConstraint(name = "uk_disbursement_installment_sequence", columnNames = {"plan_id", "installment_number"})
})
public class DisbursementInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private DisbursementPlan plan;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "planned_release_date")
    private LocalDate plannedReleaseDate;

    @Column(name = "actual_disbursement_date")
    private LocalDateTime actualDisbursementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InstallmentStatus status = InstallmentStatus.PLANNED;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursed_by_user_id")
    private User disbursedBy;

    @Column(name = "disbursement_remarks", columnDefinition = "TEXT")
    private String disbursementRemarks;

    @OneToMany(mappedBy = "installment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dueDate ASC")
    private List<DisbursementMilestone> milestones = new ArrayList<>();

    public DisbursementInstallment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DisbursementPlan getPlan() { return plan; }
    public void setPlan(DisbursementPlan plan) { this.plan = plan; }

    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPlannedReleaseDate() { return plannedReleaseDate; }
    public void setPlannedReleaseDate(LocalDate plannedReleaseDate) { this.plannedReleaseDate = plannedReleaseDate; }

    public LocalDateTime getActualDisbursementDate() { return actualDisbursementDate; }
    public void setActualDisbursementDate(LocalDateTime actualDisbursementDate) { this.actualDisbursementDate = actualDisbursementDate; }

    public InstallmentStatus getStatus() { return status; }
    public void setStatus(InstallmentStatus status) { this.status = status; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public User getDisbursedBy() { return disbursedBy; }
    public void setDisbursedBy(User disbursedBy) { this.disbursedBy = disbursedBy; }

    public String getDisbursementRemarks() { return disbursementRemarks; }
    public void setDisbursementRemarks(String disbursementRemarks) { this.disbursementRemarks = disbursementRemarks; }

    public List<DisbursementMilestone> getMilestones() { return milestones; }
    public void setMilestones(List<DisbursementMilestone> milestones) {
        this.milestones.clear();
        if (milestones != null) {
            milestones.forEach(m -> {
                m.setInstallment(this);
                this.milestones.add(m);
            });
        }
    }

    public void addMilestone(DisbursementMilestone milestone) {
        milestone.setInstallment(this);
        this.milestones.add(milestone);
    }
}
