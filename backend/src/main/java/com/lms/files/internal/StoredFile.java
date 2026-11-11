package com.lms.files.internal;

import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** The API's record of one object in S3: what was declared, and whether it was verified. */
@Entity
@Table(name = "stored_file")
class StoredFile extends AbstractEntity {

    enum Purpose { VIDEO_SOURCE, DOCUMENT }

    enum Status { PENDING, UPLOADED, ABORTED }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Purpose purpose;

    @Column(name = "object_key", nullable = false, updatable = false, length = 512)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "multipart_upload_id", length = 1024)
    private String multipartUploadId;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    protected StoredFile() {
    }

    StoredFile(UUID tenantId, Purpose purpose, String fileName, String contentType, long sizeBytes, UUID uploadedBy) {
        this.purpose = purpose;
        // Key built from ids only: the client's file name never reaches S3 (no path tricks,
        // no encoding surprises). The tenant prefix lets IAM and lifecycle rules work per company.
        this.objectKey = "tenants/" + tenantId + "/files/" + getId() + "/source";
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = Status.PENDING;
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    void openMultipart(String uploadId) {
        this.multipartUploadId = uploadId;
    }

    void markUploaded() {
        this.status = Status.UPLOADED;
        this.multipartUploadId = null;
        this.uploadedAt = Instant.now();
    }

    void markAborted() {
        this.status = Status.ABORTED;
        this.multipartUploadId = null;
    }

    boolean isMultipart() {
        return multipartUploadId != null;
    }

    UUID getTenantId() {
        return tenantId;
    }

    Purpose getPurpose() {
        return purpose;
    }

    String getObjectKey() {
        return objectKey;
    }

    String getFileName() {
        return fileName;
    }

    String getContentType() {
        return contentType;
    }

    long getSizeBytes() {
        return sizeBytes;
    }

    Status getStatus() {
        return status;
    }

    String getMultipartUploadId() {
        return multipartUploadId;
    }

    UUID getUploadedBy() {
        return uploadedBy;
    }
}
