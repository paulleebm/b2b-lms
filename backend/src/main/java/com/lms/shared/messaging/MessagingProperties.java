package com.lms.shared.messaging;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint      empty in AWS; locally ElasticMQ (an SQS-compatible server)
 * @param queues        logical name → SQS queue name, e.g. events → lms-events
 * @param workerEnabled the API service runs with this off in AWS; the worker service with it on.
 *                      Locally one process does both.
 */
@ConfigurationProperties(prefix = "lms.messaging")
public record MessagingProperties(String endpoint, String region, Map<String, String> queues,
                                  boolean workerEnabled, Duration pollWait, int maxMessages) {

    public boolean usesLocalEndpoint() {
        return endpoint != null && !endpoint.isBlank();
    }
}
