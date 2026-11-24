package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "compliance_review_history", indexes = @Index(name = "ix_compliance_history_record_time", columnList = "compliance_id, occurred_at"))
public class ComplianceReviewHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compliance_id", nullable = false) private MilestoneCompliance compliance;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id") private User actor;
    @Column(nullable = false, length = 40) private String action;
    @Column(columnDefinition = "TEXT") private String remarks;
    @Column(name = "document_file_name", length = 255) private String documentFileName;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt = LocalDateTime.now();
    public Long getId(){return id;} public MilestoneCompliance getCompliance(){return compliance;} public void setCompliance(MilestoneCompliance v){compliance=v;}
    public User getActor(){return actor;} public void setActor(User v){actor=v;} public String getAction(){return action;} public void setAction(String v){action=v;}
    public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;} public String getDocumentFileName(){return documentFileName;} public void setDocumentFileName(String v){documentFileName=v;}
    public LocalDateTime getOccurredAt(){return occurredAt;}
}
