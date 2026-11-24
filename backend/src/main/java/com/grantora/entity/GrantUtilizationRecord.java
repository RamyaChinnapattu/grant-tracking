package com.grantora.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="grant_utilization_records", indexes={
    @Index(name="ix_utilization_application",columnList="application_id"),
    @Index(name="ix_utilization_installment",columnList="installment_id"),
    @Index(name="ix_utilization_status",columnList="verification_status")
})
public class GrantUtilizationRecord {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="application_id",nullable=false) private GrantApplication application;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="installment_id",nullable=false) private DisbursementInstallment installment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="submitted_by_user_id",nullable=false) private User submittedBy;
    @Column(name="reported_amount",nullable=false,precision=12,scale=2) private BigDecimal reportedAmount;
    @Column(nullable=false,length=100) private String category;
    @Column(columnDefinition="TEXT") private String description;
    @ManyToMany(fetch=FetchType.LAZY)
    @JoinTable(name="utilization_supporting_documents",
        joinColumns=@JoinColumn(name="utilization_id"),inverseJoinColumns=@JoinColumn(name="compliance_id"),
        uniqueConstraints=@UniqueConstraint(name="uk_utilization_supporting_document",columnNames={"utilization_id","compliance_id"}))
    private Set<MilestoneCompliance> supportingDocuments=new LinkedHashSet<>();
    @Enumerated(EnumType.STRING) @Column(name="verification_status",nullable=false,length=30) private GrantUtilizationStatus status=GrantUtilizationStatus.SUBMITTED;
    @Column(name="submitted_at",nullable=false) private LocalDateTime submittedAt=LocalDateTime.now();
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="verified_by_user_id") private User verifiedBy;
    @Column(name="verified_at") private LocalDateTime verifiedAt;
    @Column(name="review_remarks",columnDefinition="TEXT") private String reviewRemarks;
    @OneToMany(mappedBy="utilization",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("occurredAt ASC") private List<UtilizationReviewHistory> history=new ArrayList<>();
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public GrantApplication getApplication(){return application;} public void setApplication(GrantApplication v){application=v;}
    public DisbursementInstallment getInstallment(){return installment;} public void setInstallment(DisbursementInstallment v){installment=v;}
    public User getSubmittedBy(){return submittedBy;} public void setSubmittedBy(User v){submittedBy=v;}
    public BigDecimal getReportedAmount(){return reportedAmount;} public void setReportedAmount(BigDecimal v){reportedAmount=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getDescription(){return description;} public void setDescription(String v){description=v;}
    public Set<MilestoneCompliance> getSupportingDocuments(){return supportingDocuments;} public void setSupportingDocuments(Set<MilestoneCompliance> v){supportingDocuments=v==null?new LinkedHashSet<>():new LinkedHashSet<>(v);}
    public GrantUtilizationStatus getStatus(){return status;} public void setStatus(GrantUtilizationStatus v){status=v;}
    public LocalDateTime getSubmittedAt(){return submittedAt;} public void setSubmittedAt(LocalDateTime v){submittedAt=v;}
    public User getVerifiedBy(){return verifiedBy;} public void setVerifiedBy(User v){verifiedBy=v;}
    public LocalDateTime getVerifiedAt(){return verifiedAt;} public void setVerifiedAt(LocalDateTime v){verifiedAt=v;}
    public String getReviewRemarks(){return reviewRemarks;} public void setReviewRemarks(String v){reviewRemarks=v;}
    public List<UtilizationReviewHistory> getHistory(){return history;} public void addHistory(UtilizationReviewHistory v){v.setUtilization(this);history.add(v);}
}
