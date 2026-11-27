package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiary_email_logs", indexes = {
    @Index(name = "ix_email_log_app", columnList = "application_id"),
    @Index(name = "ix_email_log_beneficiary", columnList = "beneficiary_id"),
    @Index(name = "ix_email_log_status", columnList = "status"),
    @Index(name = "ix_email_log_idempotency", columnList = "idempotency_key", unique = true)
})
public class BeneficiaryEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "recipient_email", nullable = false, length = 150)
    private String recipientEmail;

    @Column(name = "notification_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private BeneficiaryEmailType notificationType;

    @Column(name = "subject", nullable = false, length = 250)
    private String subject;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 180)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EmailDeliveryStatus status = EmailDeliveryStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public BeneficiaryEmailLog() {
    }

    public BeneficiaryEmailLog(Long beneficiaryId, Long applicationId, String recipientEmail,
                               BeneficiaryEmailType notificationType, String subject, String idempotencyKey) {
        this.beneficiaryId = beneficiaryId;
        this.applicationId = applicationId;
        this.recipientEmail = recipientEmail;
        this.notificationType = notificationType;
        this.subject = subject;
        this.idempotencyKey = idempotencyKey;
        this.status = EmailDeliveryStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public void setBeneficiaryId(Long beneficiaryId) {
        this.beneficiaryId = beneficiaryId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public BeneficiaryEmailType getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(BeneficiaryEmailType notificationType) {
        this.notificationType = notificationType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public EmailDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(EmailDeliveryStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void markSent() {
        this.status = EmailDeliveryStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = EmailDeliveryStatus.FAILED;
        this.failureReason = reason;
    }
}
