package com.grantora.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "utilization_document_versions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_utilization_doc_version", columnNames = {"utilization_id", "version_number"})
})
public class UtilizationDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilization_id", nullable = false)
    private GrantUtilizationRecord utilization;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "document_file_name", nullable = false, length = 255)
    private String documentFileName;

    @Column(name = "document_content_type", length = 100)
    private String documentContentType;

    @Column(name = "document_file_size")
    private Long documentFileSize;

    @Lob
    @Column(name = "document_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] documentData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public UtilizationDocumentVersion() {}

    public Long getId() { return id; }
    public GrantUtilizationRecord getUtilization() { return utilization; }
    public void setUtilization(GrantUtilizationRecord utilization) { this.utilization = utilization; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public String getDocumentFileName() { return documentFileName; }
    public void setDocumentFileName(String documentFileName) { this.documentFileName = documentFileName; }
    public String getDocumentContentType() { return documentContentType; }
    public void setDocumentContentType(String documentContentType) { this.documentContentType = documentContentType; }
    public Long getDocumentFileSize() { return documentFileSize; }
    public void setDocumentFileSize(Long documentFileSize) { this.documentFileSize = documentFileSize; }
    public byte[] getDocumentData() { return documentData; }
    public void setDocumentData(byte[] documentData) { this.documentData = documentData; }
    public User getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(User uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
