package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_requirements", indexes = {
    @Index(name = "ix_compliance_requirement_scheme_active", columnList = "scheme_id, active")
})
public class ComplianceRequirement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "milestone_id")
    private DisbursementMilestone milestone;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "application_id")
    private GrantApplication application;
    @Column(nullable = false, length = 150) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(nullable = false) private boolean required = true;
    @Column(name = "submission_deadline") private LocalDate submissionDeadline;
    @Column(name = "allowed_formats", nullable = false, length = 200) private String allowedFormats = "pdf,png,jpg,jpeg";
    @Column(name = "max_file_size_bytes", nullable = false) private long maxFileSizeBytes = 10485760L;
    @Column(nullable = false) private boolean active = true;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_user_id") private User createdBy;
    @Column(nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public Scheme getScheme(){return scheme;} public void setScheme(Scheme v){scheme=v;}
    public DisbursementMilestone getMilestone(){return milestone;} public void setMilestone(DisbursementMilestone v){milestone=v;}
    public GrantApplication getApplication(){return application;} public void setApplication(GrantApplication v){application=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public boolean isRequired(){return required;} public void setRequired(boolean v){required=v;}
    public LocalDate getSubmissionDeadline(){return submissionDeadline;} public void setSubmissionDeadline(LocalDate v){submissionDeadline=v;}
    public String getAllowedFormats(){return allowedFormats;} public void setAllowedFormats(String v){allowedFormats=v;}
    public long getMaxFileSizeBytes(){return maxFileSizeBytes;} public void setMaxFileSizeBytes(long v){maxFileSizeBytes=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public User getCreatedBy(){return createdBy;} public void setCreatedBy(User v){createdBy=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
