package com.lms.files.internal;

import com.lms.files.MediaLibrary;
import com.lms.files.MediaLibrary.VideoState;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MediaLibraryService implements MediaLibrary {

    /** Long enough to watch a long lecture; short enough that a leaked link soon stops working. */
    private static final java.time.Duration PLAYBACK_TTL = java.time.Duration.ofHours(2);

    private final VideoAssetRepository videos;
    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final StorageProperties props;
    private final CloudFrontCookies cdn;   // null locally: no CloudFront, presigned S3 GET instead

    MediaLibraryService(VideoAssetRepository videos, StoredFileRepository files, ObjectStorage storage,
                        StorageProperties props,
                        @org.springframework.beans.factory.annotation.Value("${lms.media.cdn.base-url:}") String cdnBaseUrl,
                        @org.springframework.beans.factory.annotation.Value("${lms.media.cdn.key-pair-id:}") String keyPairId,
                        @org.springframework.beans.factory.annotation.Value("${lms.media.cdn.private-key-pem:}") String privateKeyPem,
                        @org.springframework.beans.factory.annotation.Value("${lms.media.cdn.cookie-domain:}") String cookieDomain) {
        this.videos = videos;
        this.files = files;
        this.storage = storage;
        this.props = props;
        this.cdn = cdnBaseUrl.isBlank() ? null : new CloudFrontCookies(cdnBaseUrl, keyPairId, privateKeyPem, cookieDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Playback> playback(UUID videoId) {
        return videos.findById(videoId)
                .filter(v -> v.getStatus() == VideoState.READY && v.getHlsManifestKey() != null)
                .map(v -> {
                    String key = v.getHlsManifestKey();
                    String format = key.endsWith(".m3u8") ? "hls" : "mp4";
                    if (cdn != null && format.equals("hls")) {
                        // AWS: CloudFront URL + signed cookies scoped to this video's folder (docs/05 §8).
                        String folder = key.substring(0, key.lastIndexOf('/') + 1);
                        return new Playback(cdn.url(key), format, v.getDurationSeconds(),
                                cdn.forFolder(folder, java.time.Instant.now()));
                    }
                    // Local: the fake transcoder's "rendition" is the source mp4, served by a presigned GET.
                    return new Playback(storage.presignGetInline(key, PLAYBACK_TTL).toString(), format,
                            v.getDurationSeconds(), java.util.List.of());
                });
    }

    @Override
    public String storeCertificate(UUID enrollmentId, byte[] pdf) {
        String key = "tenants/" + com.lms.shared.TenantContext.requireTenantId() + "/certificates/" + enrollmentId + ".pdf";
        storage.put(key, pdf, "application/pdf");   // same key on a retry: overwriting is harmless
        return key;
    }

    @Override
    public String downloadUrl(String objectKey, String downloadName) {
        if (!objectKey.startsWith("tenants/" + com.lms.shared.TenantContext.requireTenantId() + "/")) {
            throw new IllegalArgumentException("Object of another tenant");   // defence in depth
        }
        return storage.presignGet(objectKey, downloadName, props.downloadUrlTtl()).toString();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<DocumentLink> documentLink(UUID fileId) {
        return files.findById(fileId)
                .filter(f -> f.getPurpose() == StoredFile.Purpose.DOCUMENT && f.getStatus() == StoredFile.Status.UPLOADED)
                .map(f -> new DocumentLink(storage.presignGet(f.getObjectKey(), f.getFileName(), props.downloadUrlTtl()).toString(),
                        f.getFileName(), f.getContentType(), f.getSizeBytes()));
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
