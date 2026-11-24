package com.grantora.entity;

import com.grantora.dto.EligibilityEvaluationResult;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eligibility_evaluation_history", indexes = {
        @Index(name = "ix_eligibility_history_application", columnList = "application_id, evaluated_at")
})
public class EligibilityEvaluationHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private GrantApplication application;
    @Column(name = "configuration_version", nullable = false)
    private int configurationVersion;
    @Convert(converter = EligibilityEvaluationResultConverter.class)
    @Column(name = "evaluation_result", nullable = false, columnDefinition = "TEXT")
    private EligibilityEvaluationResult result;
    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist void beforeInsert() {
        if (evaluatedAt == null) evaluatedAt = result != null && result.evaluatedAt() != null
                ? result.evaluatedAt() : LocalDateTime.now();
    }
    public Long getId() { return id; }
    public GrantApplication getApplication() { return application; }
    public void setApplication(GrantApplication application) { this.application = application; }
    public int getConfigurationVersion() { return configurationVersion; }
    public void setConfigurationVersion(int configurationVersion) { this.configurationVersion = configurationVersion; }
    public EligibilityEvaluationResult getResult() { return result; }
    public void setResult(EligibilityEvaluationResult result) { this.result = result; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
