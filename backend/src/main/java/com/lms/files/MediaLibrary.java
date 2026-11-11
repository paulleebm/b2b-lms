package com.lms.files;

import com.lms.shared.web.ApiNullable;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask the files module: "what state are these videos/documents in?".
 * Batch lookups, so a course outline with 40 lessons costs two queries, not 40.
 * Every lookup is tenant-scoped (Hibernate + RLS): another company's id is simply absent.
 */
public interface MediaLibrary {

    enum VideoState { PROCESSING, READY, FAILED }

    record VideoSummary(UUID id, VideoState state, @ApiNullable Integer durationSeconds, @ApiNullable String fileName) {
    }

    /** {@code available} = the upload finished and was verified. */
    record DocumentSummary(UUID id, String fileName, String contentType, long sizeBytes, boolean available) {
    }

    Map<UUID, VideoSummary> videos(Collection<UUID> videoIds);

    Map<UUID, DocumentSummary> documents(Collection<UUID> fileIds);

    /**
     * Where a learner's player fetches the video. {@code format}: "hls" (AWS: CloudFront + signed
     * cookies) or "mp4" (local: a short-lived presigned GET of the source). {@code durationSeconds}
     * is the server's value: progress is measured against it, never against what the browser says.
     */
    record Playback(String url, String format, @ApiNullable Integer durationSeconds, java.util.List<SignedCookie> cookies) {
    }

    /** A CloudFront signed cookie (AWS only). The API sends it as Set-Cookie; the value never goes into JSON. */
    record SignedCookie(String name, String value, String domain, String path, java.time.Instant expiresAt) {

        public String toSetCookieHeader() {
            return name + "=" + value + "; Domain=" + domain + "; Path=" + path + "; Max-Age="
                    + Math.max(0, java.time.Duration.between(java.time.Instant.now(), expiresAt).toSeconds())
                    + "; Secure; HttpOnly; SameSite=Lax";
        }
    }

    /** READY videos only. */
    Optional<Playback> playback(UUID videoId);

    record DocumentLink(String url, String fileName, String contentType, long sizeBytes) {
    }

    /** Verified documents only: a short-lived download link. */
    Optional<DocumentLink> documentLink(UUID fileId);

    /** Stores a generated certificate PDF; returns its object key (tenants/{t}/certificates/{id}.pdf). */
    String storeCertificate(UUID enrollmentId, byte[] pdf);

    /** A 5-minute download link for a stored object, saved as {@code downloadName}. */
    String downloadUrl(String objectKey, String downloadName);
}
