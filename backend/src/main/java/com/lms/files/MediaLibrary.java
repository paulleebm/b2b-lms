package com.lms.files;

import com.lms.shared.web.ApiNullable;
import java.util.Collection;
import java.util.Map;
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
}
