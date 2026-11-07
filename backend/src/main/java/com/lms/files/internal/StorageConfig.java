package com.lms.files.internal;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * One code path for AWS, home and local: only the endpoint and credentials differ.
 * <ul>
 *   <li>AWS: the ECS task role supplies short-lived credentials (DefaultCredentialsProvider);
 *       no access keys exist anywhere in configuration.</li>
 *   <li>Home: Cloudflare R2 (S3 API) with an R2 API token's key pair from the stack's env.</li>
 *   <li>Local: S3Mock with dummy keys.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties props) {
        var builder = S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props));
        if (props.usesLocalEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                    // SDK 2.30+ adds CRC checksums to every upload by default. S3-compatible
                    // services differ in which ones they accept, so send them only when an
                    // operation requires one.
                    .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties props) {
        var builder = S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(credentials(props));
        if (props.usesLocalEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties props) {
        if (props.hasStaticKeys()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey()));
        }
        return props.usesLocalEndpoint()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))
                : DefaultCredentialsProvider.builder().build();
    }
}
