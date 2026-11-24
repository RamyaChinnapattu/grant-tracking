package com.grantora.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "scheme_document_requirements")
public class SchemeDocumentRequirement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false)
    private boolean required = true;
    @Column(name = "condition_field", length = 40)
    private String conditionField;
    @Column(name = "condition_value", length = 200)
    private String conditionValue;
    @Column(name = "allowed_formats", nullable = false, length = 300)
    private String allowedFormats = "pdf,png,jpg,jpeg";
    @Column(name = "max_file_size_bytes", nullable = false)
    private long maxFileSizeBytes = 10485760L;
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public String getConditionField() { return conditionField; }
    public void setConditionField(String conditionField) { this.conditionField = conditionField; }
    public String getConditionValue() { return conditionValue; }
    public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }
    public String getAllowedFormats() { return allowedFormats; }
    public void setAllowedFormats(String allowedFormats) { this.allowedFormats = allowedFormats; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
