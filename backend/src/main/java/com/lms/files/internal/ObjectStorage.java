package com.lms.files.internal;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** The handful of S3 calls the upload flow needs, in one place. */
@Component
class ObjectStorage {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    ObjectStorage(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    /**
     * Content-Type and Content-Length are part of the signature: the browser must send exactly
     * what was declared, so a 10 MB "PDF" URL cannot be used to push 5 GB of something else.
     */
    URL presignPut(String key, String contentType, long sizeBytes) {
        return presigner.presignPutObject(r -> r
                .signatureDuration(props.uploadUrlTtl())
                .putObjectRequest(p -> p.bucket(props.bucket()).key(key)
                        .contentType(contentType).contentLength(sizeBytes)))
                .url();
    }

    String createMultipart(String key, String contentType) {
        return s3.createMultipartUpload(r -> r.bucket(props.bucket()).key(key).contentType(contentType)).uploadId();
    }

    URL presignPart(String key, String uploadId, int partNumber) {
        return presigner.presignUploadPart(r -> r
                .signatureDuration(props.uploadUrlTtl())
                .uploadPartRequest(p -> p.bucket(props.bucket()).key(key).uploadId(uploadId).partNumber(partNumber)))
                .url();
    }

    void completeMultipart(String key, String uploadId, List<CompletedPart> parts) {
        s3.completeMultipartUpload(r -> r.bucket(props.bucket()).key(key).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build()));
    }

    void abortMultipart(String key, String uploadId) {
        s3.abortMultipartUpload(r -> r.bucket(props.bucket()).key(key).uploadId(uploadId));
    }

    Optional<HeadObjectResponse> head(String key) {
        try {
            return Optional.of(s3.headObject(r -> r.bucket(props.bucket()).key(key)));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkServiceException e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    void put(String key, byte[] bytes, String contentType) {
        s3.putObject(r -> r.bucket(props.bucket()).key(key).contentType(contentType),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(bytes));
    }

    void delete(String key) {
        s3.deleteObject(r -> r.bucket(props.bucket()).key(key));
    }

    /** Short-lived GET shown in the page (video element, PDF viewer), not downloaded. */
    URL presignGetInline(String key, Duration ttl) {
        return presigner.presignGetObject(r -> r
                .signatureDuration(ttl)
                .getObjectRequest(g -> g.bucket(props.bucket()).key(key)))
                .url();
    }

    /** Short-lived GET; the file name is set by us in Content-Disposition, not taken from the key. */
    URL presignGet(String key, String downloadFileName, Duration ttl) {
        String disposition = "attachment; filename*=UTF-8''" + java.net.URLEncoder
                .encode(downloadFileName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return presigner.presignGetObject(r -> r
                .signatureDuration(ttl)
                .getObjectRequest(g -> g.bucket(props.bucket()).key(key).responseContentDisposition(disposition)))
                .url();
    }
}
