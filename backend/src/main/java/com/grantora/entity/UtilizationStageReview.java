package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilization_stage_reviews", uniqueConstraints = {
    @UniqueConstraint(name = "uk_utilization_stage_review_cycle_stage", columnNames = {"utilization_id", "review_cycle", "stage"})
})
public class UtilizationStageReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilization_id", nullable = false)
    private GrantUtilizationRecord utilization;

    @Column(name = "review_cycle", nullable = false)
    private Integer reviewCycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private VerificationStage stage;

    @Column(name = "decision", nullable = false, length = 30)
    private String decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_user_id", nullable = false)
    private User reviewer;

    @Column(name = "reviewer_officer_level", nullable = false, length = 20)
    private String reviewerOfficerLevel;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "document_version_number")
    private Integer documentVersionNumber;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();

    public UtilizationStageReview() {}

    public Long getId() { return id; }
    public GrantUtilizationRecord getUtilization() { return utilization; }
    public void setUtilization(GrantUtilizationRecord utilization) { this.utilization = utilization; }
    public Integer getReviewCycle() { return reviewCycle; }
    public void setReviewCycle(Integer reviewCycle) { this.reviewCycle = reviewCycle; }
    public VerificationStage getStage() { return stage; }
    public void setStage(VerificationStage stage) { this.stage = stage; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public User getReviewer() { return reviewer; }
    public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    public String getReviewerOfficerLevel() { return reviewerOfficerLevel; }
    public void setReviewerOfficerLevel(String reviewerOfficerLevel) { this.reviewerOfficerLevel = reviewerOfficerLevel; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public Integer getDocumentVersionNumber() { return documentVersionNumber; }
    public void setDocumentVersionNumber(Integer documentVersionNumber) { this.documentVersionNumber = documentVersionNumber; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
