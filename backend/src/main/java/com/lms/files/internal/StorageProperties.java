package com.lms.files.internal;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint empty in AWS (regional endpoint + task role); set locally to S3Mock
 */
@ConfigurationProperties(prefix = "lms.storage")
record StorageProperties(String bucket, String region, String endpoint,
                         long singlePartMaxBytes, long partSizeBytes,
                         Duration uploadUrlTtl, Duration downloadUrlTtl) {

    boolean usesLocalEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
