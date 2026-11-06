package com.lms.files.internal;

import com.lms.shared.web.ApiNullable;
import com.lms.files.MediaLibrary.VideoState;
import com.lms.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class FileController {

    private final UploadService uploads;
    private final VideoAssetRepository videos;
    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final StorageProperties props;

    FileController(UploadService uploads, VideoAssetRepository videos, StoredFileRepository files,
                   ObjectStorage storage, StorageProperties props) {
        this.uploads = uploads;
        this.videos = videos;
        this.files = files;
        this.storage = storage;
        this.props = props;
    }

    /** Step 1: declare the file, get where to PUT it (one URL, or a multipart plan). */
    @PostMapping("/v1/uploads")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','INSTRUCTOR')")
    UploadService.InitResult init(@Valid @RequestBody InitUploadRequest request) {
        return uploads.init(request.purpose(), request.fileName(), request.contentType(), request.sizeBytes());
    }

    /** Multipart only: presigned URLs for a batch of parts (asked for as the upload progresses). */
    @PostMapping("/v1/uploads/{id}/parts")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','INSTRUCTOR')")
    PartUrlsResponse parts(@PathVariable UUID id, @Valid @RequestBody PartUrlsRequest request) {
        return new PartUrlsResponse(uploads.presignParts(id, request.partNumbers()));
    }

    /** Step 3: the API verifies the object in S3; videos then start transcoding. */
    @PostMapping("/v1/uploads/{id}/complete")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','INSTRUCTOR')")
    UploadService.CompleteResult complete(@PathVariable UUID id, @Valid @RequestBody(required = false) CompleteUploadRequest request) {
        CompleteUploadRequest body = request == null ? new CompleteUploadRequest(null, null) : request;
        return uploads.complete(id, body.partsOrEmpty(), body.durationSeconds());
    }

    @DeleteMapping("/v1/uploads/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN','INSTRUCTOR')")
    ResponseEntity<Void> abort(@PathVariable UUID id) {
        uploads.abort(id);
        return ResponseEntity.noContent().build();
    }

    /** Polled by the studio while a video is processing. */
    @GetMapping("/v1/videos/{id}")
    @Transactional(readOnly = true)
    VideoResponse video(@PathVariable UUID id) {
        VideoAsset v = videos.findById(id)
                .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다."));
        return new VideoResponse(v.getId(), v.getStatus(), v.getDurationSeconds(), v.getFailureReason());
    }

    /**
     * A 5-minute download link. Day 3 narrows this to people enrolled in a course that uses the file;
     * for now: any active member of the company that owns it (tenant scoping already applies).
     */
    @GetMapping("/v1/files/{id}/download-url")
    @Transactional(readOnly = true)
    DownloadResponse download(@PathVariable UUID id) {
        StoredFile f = files.findById(id)
                .filter(file -> file.getStatus() == StoredFile.Status.UPLOADED
                        && file.getPurpose() == StoredFile.Purpose.DOCUMENT)
                .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "파일을 찾을 수 없습니다."));
        return new DownloadResponse(storage.presignGet(f.getObjectKey(), f.getFileName(), props.downloadUrlTtl()),
                Instant.now().plus(props.downloadUrlTtl()));
    }

    record InitUploadRequest(
            @NotNull StoredFile.Purpose purpose,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @Positive long sizeBytes) {
    }

    record PartUrlsRequest(@NotEmpty @Size(max = 100) List<@NotNull @Min(1) @Max(10000) Integer> partNumbers) {
    }

    record PartUrlsResponse(List<UploadService.PartUrl> parts) {
    }

    record CompleteUploadRequest(
            @Size(max = 10000) List<@Valid PartInput> parts,
            @Min(0) Integer durationSeconds) {

        List<UploadService.CompletedPartInput> partsOrEmpty() {
            return parts == null ? List.of() : parts.stream()
                    .map(p -> new UploadService.CompletedPartInput(p.partNumber(), p.etag())).toList();
        }
    }

    record PartInput(@Min(1) @Max(10000) int partNumber, @NotBlank String etag) {
    }

    record VideoResponse(UUID id, VideoState status, @ApiNullable Integer durationSeconds, @ApiNullable String failureReason) {
    }

    record DownloadResponse(URL url, Instant expiresAt) {
    }
}
