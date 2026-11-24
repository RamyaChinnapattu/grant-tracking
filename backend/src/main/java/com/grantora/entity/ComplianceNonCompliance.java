package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "compliance_non_compliance", uniqueConstraints = @UniqueConstraint(name = "uk_non_compliance_obligation", columnNames = "compliance_id"))
public class ComplianceNonCompliance {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "compliance_id", nullable = false) private MilestoneCompliance compliance;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false, length = 20) private String severity = "MEDIUM";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private NonComplianceStatus status = NonComplianceStatus.OPEN;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "detected_by_user_id") private User detectedBy;
    @Column(nullable = false) private LocalDateTime detectedAt = LocalDateTime.now();
    @Column(columnDefinition = "TEXT") private String officerRemarks;
    @OneToMany(mappedBy = "nonCompliance", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("occurredAt ASC") private List<NonComplianceHistory> history = new ArrayList<>();
    public Long getId(){return id;} public MilestoneCompliance getCompliance(){return compliance;} public void setCompliance(MilestoneCompliance v){compliance=v;}
    public String getReason(){return reason;} public void setReason(String v){reason=v;} public String getSeverity(){return severity;} public void setSeverity(String v){severity=v;}
    public NonComplianceStatus getStatus(){return status;} public void setStatus(NonComplianceStatus v){status=v;} public User getDetectedBy(){return detectedBy;} public void setDetectedBy(User v){detectedBy=v;}
    public LocalDateTime getDetectedAt(){return detectedAt;} public String getOfficerRemarks(){return officerRemarks;} public void setOfficerRemarks(String v){officerRemarks=v;}
    public List<NonComplianceHistory> getHistory(){return history;} public void addHistory(NonComplianceHistory v){v.setNonCompliance(this);history.add(v);}
}
