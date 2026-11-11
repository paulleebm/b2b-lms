package com.lms.shared.messaging;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/** SQS client (ElasticMQ locally) and queue URL lookup. {@code @EnableAsync}: the outbox relay runs after commit, off the request thread. */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class MessagingConfig {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(MessagingProperties props) {
        var builder = SqsClient.builder().region(Region.of(props.region()));
        if (props.usesLocalEndpoint()) {
            builder.endpointOverride(URI.create(props.endpoint()))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        return builder.build();
    }

    @Bean
    QueueUrls queueUrls(SqsClient sqs, MessagingProperties props) {
        return new QueueUrls(sqs, props.queues());
    }

    /** Resolves queue names to URLs once (GetQueueUrl), then caches them. */
    static final class QueueUrls {

        private final SqsClient sqs;
        private final Map<String, String> names;
        private final Map<String, String> urls = new ConcurrentHashMap<>();

        QueueUrls(SqsClient sqs, Map<String, String> names) {
            this.sqs = sqs;
            this.names = names;
        }

        String url(String key) {
            return urls.computeIfAbsent(key, k -> {
                String name = names.get(k);
                if (name == null) {
                    throw new IllegalStateException("No queue configured for '" + k + "'");
                }
                return sqs.getQueueUrl(r -> r.queueName(name)).queueUrl();
            });
        }

        java.util.Set<String> keys() {
            return names.keySet();
        }
    }
}
