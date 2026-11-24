package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "milestone_compliances")
public class MilestoneCompliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "milestone_id", nullable = false)
    private DisbursementMilestone milestone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    private ComplianceRequirement requirement;

    @Column(name = "required_obligation")
    private Boolean required = true;

    @Column(name = "submission_deadline")
    private LocalDate submissionDeadline;

    @Column(name = "requirement_title", nullable = false, length = 150)
    private String requirementTitle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "submission_notes", columnDefinition = "TEXT")
    private String submissionNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ComplianceStatus status = ComplianceStatus.PENDING;

    @Column(name = "document_file_name", length = 255)
    private String documentFileName;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Column(name = "document_file_size")
    private Long documentFileSize;

    @Lob
    @Column(name = "document_data", columnDefinition = "LONGBLOB")
    private byte[] documentData;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id")
    private User submittedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "verification_remarks", columnDefinition = "TEXT")
    private String verificationRemarks;

    @OneToMany(mappedBy = "compliance", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<ComplianceReviewHistory> reviewHistory = new ArrayList<>();

    public MilestoneCompliance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DisbursementMilestone getMilestone() { return milestone; }
    public void setMilestone(DisbursementMilestone milestone) { this.milestone = milestone; }
    public ComplianceRequirement getRequirement() { return requirement; }
    public void setRequirement(ComplianceRequirement requirement) { this.requirement = requirement; }
    public boolean isRequired() { return !Boolean.FALSE.equals(required); }
    public void setRequired(Boolean required) { this.required = required; }
    public LocalDate getSubmissionDeadline() { return submissionDeadline; }
    public void setSubmissionDeadline(LocalDate submissionDeadline) { this.submissionDeadline = submissionDeadline; }

    public String getRequirementTitle() { return requirementTitle; }
    public void setRequirementTitle(String requirementTitle) { this.requirementTitle = requirementTitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSubmissionNotes() { return submissionNotes; }
    public void setSubmissionNotes(String submissionNotes) { this.submissionNotes = submissionNotes; }

    public ComplianceStatus getStatus() { return status; }
    public void setStatus(ComplianceStatus status) { this.status = status; }

    public String getDocumentFileName() { return documentFileName; }
    public void setDocumentFileName(String documentFileName) { this.documentFileName = documentFileName; }

    public String getDocumentContentType() { return documentContentType; }
    public void setDocumentContentType(String documentContentType) { this.documentContentType = documentContentType; }

    public Long getDocumentFileSize() { return documentFileSize; }
    public void setDocumentFileSize(Long documentFileSize) { this.documentFileSize = documentFileSize; }

    public byte[] getDocumentData() { return documentData; }
    public void setDocumentData(byte[] documentData) { this.documentData = documentData; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public User getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(User submittedBy) { this.submittedBy = submittedBy; }

    public User getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(User verifiedBy) { this.verifiedBy = verifiedBy; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public String getVerificationRemarks() { return verificationRemarks; }
    public void setVerificationRemarks(String verificationRemarks) { this.verificationRemarks = verificationRemarks; }
    public List<ComplianceReviewHistory> getReviewHistory() { return reviewHistory; }
    public void addHistory(ComplianceReviewHistory entry) { entry.setCompliance(this); reviewHistory.add(entry); }
}
