package com.lms.files.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint empty in AWS (regional endpoint + task role); set locally to S3Mock and at home
 *                 to Cloudflare R2 ({@code https://<account>.r2.cloudflarestorage.com})
 * @param accessKeyId empty in AWS and locally; the R2 API token's access key at home
 */
@ConfigurationProperties(prefix = "lms.storage")
record StorageProperties(String bucket, String region, String endpoint,
                         String accessKeyId, String secretAccessKey,
                         long singlePartMaxBytes, long partSizeBytes,
                         Duration uploadUrlTtl, Duration downloadUrlTtl) {

    boolean usesLocalEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }

    boolean hasStaticKeys() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }
}
