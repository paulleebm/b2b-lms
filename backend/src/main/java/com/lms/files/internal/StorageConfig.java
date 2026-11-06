package com.lms.files.internal;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * One code path for AWS and local: only the endpoint and credentials differ.
 * In AWS the ECS task role supplies short-lived credentials (DefaultCredentialsProvider);
 * no access keys exist anywhere in configuration.
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
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
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
        return props.usesLocalEndpoint()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))
                : DefaultCredentialsProvider.builder().build();
    }
}
