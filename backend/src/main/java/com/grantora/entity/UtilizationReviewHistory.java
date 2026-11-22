package com.grantora.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="utilization_review_history")
public class UtilizationReviewHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="utilization_id",nullable=false) private GrantUtilizationRecord utilization;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_user_id") private User actor;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private GrantUtilizationStatus status;
    @Column(columnDefinition="TEXT") private String remarks;
    @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt=LocalDateTime.now();
    public Long getId(){return id;} public GrantUtilizationRecord getUtilization(){return utilization;} public void setUtilization(GrantUtilizationRecord v){utilization=v;}
    public User getActor(){return actor;} public void setActor(User v){actor=v;} public GrantUtilizationStatus getStatus(){return status;} public void setStatus(GrantUtilizationStatus v){status=v;}
    public String getRemarks(){return remarks;} public void setRemarks(String v){remarks=v;} public LocalDateTime getOccurredAt(){return occurredAt;}
}
