package com.lms.files.internal;

import com.lms.files.MediaLibrary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MediaLibraryService implements MediaLibrary {

    private final VideoAssetRepository videos;
    private final StoredFileRepository files;

    MediaLibraryService(VideoAssetRepository videos, StoredFileRepository files) {
        this.videos = videos;
        this.files = files;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VideoSummary> videos(Collection<UUID> videoIds) {
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        var found = videos.findAllByIdIn(videoIds);
        Map<UUID, StoredFile> sources = files.findAllByIdIn(found.stream().map(VideoAsset::getSourceFileId).toList())
                .stream().collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        return found.stream().collect(Collectors.toMap(VideoAsset::getId, v -> new VideoSummary(
                v.getId(), v.getStatus(), v.getDurationSeconds(),
                sources.containsKey(v.getSourceFileId()) ? sources.get(v.getSourceFileId()).getFileName() : null)));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, DocumentSummary> documents(Collection<UUID> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return files.findAllByIdIn(fileIds).stream()
                .filter(f -> f.getPurpose() == StoredFile.Purpose.DOCUMENT)
                .collect(Collectors.toMap(StoredFile::getId, f -> new DocumentSummary(
                        f.getId(), f.getFileName(), f.getContentType(), f.getSizeBytes(),
                        f.getStatus() == StoredFile.Status.UPLOADED)));
    }
}
