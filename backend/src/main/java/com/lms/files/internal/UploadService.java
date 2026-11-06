package com.lms.files.internal;

import com.lms.shared.web.ApiNullable;
import com.lms.shared.TenantContext;
import com.lms.shared.web.ApiException;
import java.net.URL;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

/**
 * Direct-to-S3 uploads in three steps: <b>init</b> (we record the declared file and hand out
 * presigned URL(s)) → the browser uploads the bytes to S3 → <b>complete</b> (we check what
 * actually arrived before anything may use it).
 */
@Service
class UploadService {

    /** Allowlists: decided by purpose, never by what the file name claims. */
    private static final Map<StoredFile.Purpose, Set<String>> ALLOWED_TYPES = Map.of(
            StoredFile.Purpose.VIDEO_SOURCE, Set.of("video/mp4", "video/quicktime", "video/webm", "video/x-matroska"),
            StoredFile.Purpose.DOCUMENT, Set.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "text/plain"));

    private static final Map<StoredFile.Purpose, Long> MAX_BYTES = Map.of(
            StoredFile.Purpose.VIDEO_SOURCE, 5L * 1024 * 1024 * 1024,   // 5 GiB
            StoredFile.Purpose.DOCUMENT, 50L * 1024 * 1024);            // 50 MiB

    private static final int MAX_PARTS_PER_REQUEST = 100;

    private final StoredFileRepository files;
    private final VideoAssetRepository videos;
    private final ObjectStorage storage;
    private final StorageProperties props;
    private final ApplicationEventPublisher events;

    UploadService(StoredFileRepository files, VideoAssetRepository videos, ObjectStorage storage,
                  StorageProperties props, ApplicationEventPublisher events) {
        this.files = files;
        this.videos = videos;
        this.storage = storage;
        this.props = props;
        this.events = events;
    }

    enum Mode { SINGLE, MULTIPART }

    record InitResult(UUID fileId, Mode mode, @ApiNullable URL url, @ApiNullable Map<String, String> headers,
                      @ApiNullable Long partSizeBytes, @ApiNullable Integer partCount, Instant expiresAt) {
    }

    @Transactional
    InitResult init(StoredFile.Purpose purpose, String rawFileName, String rawContentType, long sizeBytes) {
        String contentType = rawContentType.strip().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.get(purpose).contains(contentType)) {
            throw ApiException.unprocessable("UNSUPPORTED_FILE_TYPE", "올릴 수 없는 파일 형식입니다.")
                    .withErrors(List.of(Map.of("allowed", ALLOWED_TYPES.get(purpose).stream().sorted().toList())));
        }
        if (sizeBytes > MAX_BYTES.get(purpose)) {
            throw ApiException.unprocessable("FILE_TOO_LARGE", "파일이 너무 큽니다. 최대 "
                    + (MAX_BYTES.get(purpose) / (1024 * 1024)) + "MB까지 올릴 수 있습니다.");
        }

        StoredFile file = new StoredFile(TenantContext.requireTenantId(), purpose, cleanFileName(rawFileName),
                contentType, sizeBytes, TenantContext.requireMembershipId());
        Instant expiresAt = Instant.now().plus(props.uploadUrlTtl());

        if (sizeBytes <= props.singlePartMaxBytes()) {
            files.save(file);
            URL url = storage.presignPut(file.getObjectKey(), contentType, sizeBytes);
            return new InitResult(file.getId(), Mode.SINGLE, url, Map.of("Content-Type", contentType),
                    null, null, expiresAt);
        }
        // Large files: S3 multipart, so a dropped connection costs one 16 MiB part, not the whole file,
        // and parts can go up in parallel.
        file.openMultipart(storage.createMultipart(file.getObjectKey(), contentType));
        files.save(file);
        int partCount = (int) Math.ceilDiv(sizeBytes, props.partSizeBytes());
        return new InitResult(file.getId(), Mode.MULTIPART, null, null, props.partSizeBytes(), partCount, expiresAt);
    }

    record PartUrl(int partNumber, URL url) {
    }

    @Transactional(readOnly = true)
    List<PartUrl> presignParts(UUID fileId, List<Integer> partNumbers) {
        StoredFile file = pending(fileId);
        if (!file.isMultipart()) {
            throw ApiException.conflict("NOT_MULTIPART", "단일 업로드에는 파트 URL이 필요하지 않습니다.");
        }
        int partCount = (int) Math.ceilDiv(file.getSizeBytes(), props.partSizeBytes());
        if (partNumbers.size() > MAX_PARTS_PER_REQUEST
                || partNumbers.stream().anyMatch(n -> n < 1 || n > partCount)) {
            throw ApiException.badRequest("INVALID_PART_NUMBER", "파트 번호는 1부터 " + partCount + "까지입니다.");
        }
        return partNumbers.stream().distinct().sorted()
                .map(n -> new PartUrl(n, storage.presignPart(file.getObjectKey(), file.getMultipartUploadId(), n)))
                .toList();
    }

    record CompletedPartInput(int partNumber, String etag) {
    }

    record CompleteResult(UUID fileId, StoredFile.Status status, @ApiNullable UUID videoId) {
    }

    /**
     * Never trust the client's "done": the object must exist and match the declared size and type.
     * A mismatch deletes the object and aborts the record.
     */
    @Transactional(noRollbackFor = ApiException.class)
    CompleteResult complete(UUID fileId, List<CompletedPartInput> parts, Integer durationHintSeconds) {
        StoredFile file = pending(fileId);

        if (file.isMultipart()) {
            if (parts == null || parts.isEmpty()) {
                throw ApiException.badRequest("PARTS_REQUIRED", "업로드한 파트 목록이 필요합니다.");
            }
            List<CompletedPart> completed = parts.stream()
                    .sorted(Comparator.comparingInt(CompletedPartInput::partNumber))
                    .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build())
                    .toList();
            try {
                storage.completeMultipart(file.getObjectKey(), file.getMultipartUploadId(), completed);
            } catch (RuntimeException e) {
                throw ApiException.unprocessable("UPLOAD_INCOMPLETE", "업로드가 끝나지 않았거나 일부 파트가 없습니다.");
            }
        }

        HeadObjectResponse head = storage.head(file.getObjectKey())
                .orElseThrow(() -> ApiException.unprocessable("UPLOAD_NOT_FOUND", "업로드된 파일을 찾을 수 없습니다. 다시 올려 주세요."));
        boolean sizeMatches = head.contentLength() != null && head.contentLength() == file.getSizeBytes();
        boolean typeMatches = head.contentType() != null
                && head.contentType().toLowerCase(Locale.ROOT).startsWith(file.getContentType());
        if (!sizeMatches || !typeMatches) {
            storage.delete(file.getObjectKey());
            file.markAborted();
            throw ApiException.unprocessable("UPLOAD_MISMATCH", "올라간 파일이 신고한 크기·형식과 다릅니다.");
        }

        file.markUploaded();
        UUID videoId = null;
        if (file.getPurpose() == StoredFile.Purpose.VIDEO_SOURCE) {
            Integer hint = durationHintSeconds != null && durationHintSeconds >= 0 ? durationHintSeconds : null;
            VideoAsset video = videos.save(new VideoAsset(file.getId(), hint));
            videoId = video.getId();
            // Transcoding starts after commit (VideoTranscodeTrigger), so a rollback here never
            // leaves a MediaConvert job running for a video that does not exist.
            events.publishEvent(new VideoSourceUploaded(TenantContext.requireTenantId(), video.getId(), file.getObjectKey()));
        }
        return new CompleteResult(file.getId(), file.getStatus(), videoId);
    }

    @Transactional
    void abort(UUID fileId) {
        StoredFile file = pending(fileId);
        if (file.isMultipart()) {
            storage.abortMultipart(file.getObjectKey(), file.getMultipartUploadId());
        } else {
            storage.delete(file.getObjectKey());
        }
        file.markAborted();
    }

    private StoredFile pending(UUID fileId) {
        StoredFile file = files.findById(fileId)
                .orElseThrow(() -> ApiException.notFound("UPLOAD_NOT_FOUND", "업로드를 찾을 수 없습니다."));
        if (!file.getUploadedBy().equals(TenantContext.requireMembershipId())) {
            // Same company, different uploader: still not yours to finish or cancel.
            throw ApiException.notFound("UPLOAD_NOT_FOUND", "업로드를 찾을 수 없습니다.");
        }
        if (file.getStatus() != StoredFile.Status.PENDING) {
            throw ApiException.conflict("UPLOAD_ALREADY_FINISHED", "이미 끝났거나 취소된 업로드입니다.");
        }
        return file;
    }

    static String cleanFileName(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1)          // drop any path
                .replaceAll("[\\p{Cntrl}]", "")                    // no control characters (header injection)
                .strip();
        if (name.isEmpty()) {
            name = "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /** Internal event: a verified video source is ready to be transcoded. */
    record VideoSourceUploaded(UUID tenantId, UUID videoId, String sourceKey) {
    }
}
