package com.lms.files.internal;

import com.lms.files.MediaLibrary.VideoState;
import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A playable video: its verified source upload and, once transcoded, the HLS rendition. */
@Entity
@Table(name = "video_asset")
class VideoAsset extends AbstractEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "source_file_id", nullable = false, updatable = false)
    private UUID sourceFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VideoState status;

    @Column(name = "hls_manifest_key", length = 512)
    private String hlsManifestKey;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "transcode_job_id", length = 100)
    private String transcodeJobId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    protected VideoAsset() {
    }

    /** @param durationHint what the browser read from the file's metadata; the transcoder's value wins */
    VideoAsset(UUID sourceFileId, Integer durationHint) {
        this.sourceFileId = sourceFileId;
        this.status = VideoState.PROCESSING;
        this.durationSeconds = durationHint;
        this.createdAt = Instant.now();
    }

    void jobSubmitted(String jobId) {
        this.transcodeJobId = jobId;
    }

    /** Idempotent: completion notices can arrive more than once (at-least-once delivery). */
    void markReady(String manifestKey, Integer durationSeconds) {
        if (status == VideoState.READY) {
            return;
        }
        this.status = VideoState.READY;
        this.hlsManifestKey = manifestKey;
        if (durationSeconds != null) {
            this.durationSeconds = durationSeconds;
        }
        this.failureReason = null;
        this.readyAt = Instant.now();
    }

    void markFailed(String reason) {
        if (status == VideoState.READY) {
            return;
        }
        this.status = VideoState.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getSourceFileId() {
        return sourceFileId;
    }

    VideoState getStatus() {
        return status;
    }

    Integer getDurationSeconds() {
        return durationSeconds;
    }

    String getFailureReason() {
        return failureReason;
    }

    String getHlsManifestKey() {
        return hlsManifestKey;
    }
}
