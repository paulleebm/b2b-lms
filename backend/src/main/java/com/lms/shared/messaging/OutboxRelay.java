package com.lms.shared.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox → SQS. {@link ApplicationModuleListener} = after the business transaction commits, on
 * another thread, tracked in {@code event_publication}: the publication is marked complete only
 * when this method returns. If SQS is down, it stays incomplete and the resubmission job
 * ({@code OutboxResubmitJob}) sends it again; on restart, outstanding events are resent too.
 */
@Component
class OutboxRelay {

    static final String EVENTS_QUEUE = "events";
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final SqsClient sqs;
    private final MessagingConfig.QueueUrls queues;

    OutboxRelay(SqsClient sqs, MessagingConfig.QueueUrls queues) {
        this.sqs = sqs;
        this.queues = queues;
    }

    @ApplicationModuleListener
    void relay(Externalized event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", event.eventId());
        envelope.put("type", event.messageType());
        envelope.put("tenantId", event.tenantId());
        envelope.put("occurredAt", Instant.now());
        envelope.put("payload", event);
        sqs.sendMessage(r -> r.queueUrl(queues.url(EVENTS_QUEUE)).messageBody(JSON.writeValueAsString(envelope)));
    }
}
