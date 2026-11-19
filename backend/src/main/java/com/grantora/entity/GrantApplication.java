
package com.grantora.entity;

import com.grantora.dto.EligibilityAnswer;
import com.grantora.dto.EligibilityEvaluationResult;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "grant_applications", uniqueConstraints = @UniqueConstraint(name = "uk_application_beneficiary_scheme", columnNames = {
        "beneficiary_id", "scheme_id" }))
public class GrantApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private Beneficiary beneficiary;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;

    @Column(name = "application_details", nullable = false, columnDefinition = "TEXT")
    private String applicationDetails;

    @Column(name = "requested_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrantApplicationStatus status = GrantApplicationStatus.DRAFT;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Convert(converter = EligibilityAnswerListConverter.class)
    @Column(name = "eligibility_answers", columnDefinition = "TEXT")
    private List<EligibilityAnswer> eligibilityAnswers = new ArrayList<>();

    @Convert(converter = EligibilityEvaluationResultConverter.class)
    @Column(name = "eligibility_evaluation", columnDefinition = "TEXT")
    private EligibilityEvaluationResult eligibilityEvaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_stage", length = 20)
    private VerificationStage workflowStage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_officer_id")
    private User assignedOfficer;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_priority", length = 20)
    private RoutingPriority routingPriority = RoutingPriority.NORMAL;

    private Boolean escalated = false;

    @Column(name = "task_assigned_at")
    private LocalDateTime taskAssignedAt;

    @Column(name = "task_due_at")
    private LocalDateTime taskDueAt;

    @Column(name = "task_completed_at")
    private LocalDateTime taskCompletedAt;

    public Long getId() {
        return id;
    }

    public Beneficiary getBeneficiary() {
        return beneficiary;
    }

    public void setBeneficiary(Beneficiary beneficiary) {
        this.beneficiary = beneficiary;
    }

    public Scheme getScheme() {
        return scheme;
    }

    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
    }

    public String getApplicationDetails() {
        return applicationDetails;
    }

    public void setApplicationDetails(String applicationDetails) {
        this.applicationDetails = applicationDetails;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public GrantApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(GrantApplicationStatus status) {
        this.status = status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public List<EligibilityAnswer> getEligibilityAnswers() {
        return eligibilityAnswers;
    }

    public void setEligibilityAnswers(List<EligibilityAnswer> eligibilityAnswers) {
        this.eligibilityAnswers = eligibilityAnswers == null
                ? new ArrayList<>()
                : new ArrayList<>(eligibilityAnswers);
    }

    public EligibilityEvaluationResult getEligibilityEvaluation() {
        return eligibilityEvaluation;
    }

    public void setEligibilityEvaluation(
            EligibilityEvaluationResult eligibilityEvaluation) {
        this.eligibilityEvaluation = eligibilityEvaluation;
    }

    public VerificationStage getWorkflowStage() {
        return workflowStage;
    }

    public void setWorkflowStage(VerificationStage workflowStage) {
        this.workflowStage = workflowStage;
    }

    public User getAssignedOfficer() {
        return assignedOfficer;
    }

    public void setAssignedOfficer(User assignedOfficer) {
        this.assignedOfficer = assignedOfficer;
    }

    public RoutingPriority getRoutingPriority() {
        return routingPriority;
    }

    public void setRoutingPriority(RoutingPriority routingPriority) {
        this.routingPriority = routingPriority;
    }

    public boolean isEscalated() {
        return Boolean.TRUE.equals(escalated);
    }

    public void setEscalated(boolean escalated) {
        this.escalated = escalated;
    }

    public LocalDateTime getTaskAssignedAt() {
        return taskAssignedAt;
    }

    public void setTaskAssignedAt(LocalDateTime value) {
        taskAssignedAt = value;
    }

    public LocalDateTime getTaskDueAt() {
        return taskDueAt;
    }

    public void setTaskDueAt(LocalDateTime value) {
        taskDueAt = value;
    }

    public LocalDateTime getTaskCompletedAt() {
        return taskCompletedAt;
    }

    public void setTaskCompletedAt(LocalDateTime value) {
        taskCompletedAt = value;
    }
}
