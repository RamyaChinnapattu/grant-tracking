package com.grantora.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "scheme_eligibility_rules")
public class SchemeEligibilityRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scheme_id", nullable = false)
    private Scheme scheme;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(name = "field_key", nullable = false, length = 40)
    private String fieldKey;
    @Column(nullable = false, length = 30)
    private String operator;
    @Column(name = "expected_value", nullable = false, length = 1000)
    private String expectedValue;
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Scheme getScheme() { return scheme; }
    public void setScheme(Scheme scheme) { this.scheme = scheme; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getExpectedValue() { return expectedValue; }
    public void setExpectedValue(String expectedValue) { this.expectedValue = expectedValue; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
