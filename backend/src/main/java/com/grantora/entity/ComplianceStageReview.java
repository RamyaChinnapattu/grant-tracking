package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_stage_reviews", uniqueConstraints = {
    @UniqueConstraint(name = "uk_stage_review_cycle_stage", columnNames = {"compliance_id", "review_cycle", "stage"})
}, indexes = {
    @Index(name = "ix_stage_review_compliance_cycle", columnList = "compliance_id, review_cycle")
})
public class ComplianceStageReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compliance_id", nullable = false)
    private MilestoneCompliance compliance;

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

    @Column(name = "document_file_name", length = 255)
    private String documentFileName;

    @Column(name = "document_version_number")
    private Integer documentVersionNumber;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();

    public ComplianceStageReview() {}

    public Long getId() { return id; }
    public MilestoneCompliance getCompliance() { return compliance; }
    public void setCompliance(MilestoneCompliance compliance) { this.compliance = compliance; }
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
    public String getDocumentFileName() { return documentFileName; }
    public void setDocumentFileName(String documentFileName) { this.documentFileName = documentFileName; }
    public Integer getDocumentVersionNumber() { return documentVersionNumber; }
    public void setDocumentVersionNumber(Integer documentVersionNumber) { this.documentVersionNumber = documentVersionNumber; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
