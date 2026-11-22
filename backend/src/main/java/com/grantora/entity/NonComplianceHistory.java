package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "non_compliance_history")
public class NonComplianceHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "non_compliance_id", nullable = false) private ComplianceNonCompliance nonCompliance;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id") private User actor;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private NonComplianceStatus status;
    @Column(columnDefinition = "TEXT") private String remarks;
    @Column(nullable = false) private LocalDateTime occurredAt = LocalDateTime.now();
    public Long getId(){return id;} public ComplianceNonCompliance getNonCompliance(){return nonCompliance;} public void setNonCompliance(ComplianceNonCompliance v){nonCompliance=v;}
    public User getActor(){return actor;} public void setActor(User v){actor=v;} public NonComplianceStatus getStatus(){return status;} public void setStatus(NonComplianceStatus v){status=v;}
    public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;} public LocalDateTime getOccurredAt(){return occurredAt;}
}
