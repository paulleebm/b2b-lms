package com.lms.shared.messaging;

import com.lms.shared.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Long-polls the queues on virtual threads and dispatches messages.
 *
 * <ul>
 *   <li>Our envelope ({@code events} queue): every {@link MessageHandler} of the type, each once,
 *       in the message's tenant and one transaction with its {@code processed_message} row.</li>
 *   <li>Other producers' messages (e.g. {@code transcode}: EventBridge): the {@link RawQueueHandler}.</li>
 * </ul>
 * A message is deleted only when every handler succeeded. Otherwise it reappears after the
 * visibility timeout and is retried; after 5 receives SQS moves it to the DLQ (alarm in AWS).
 */
@Component
class SqsWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SqsWorker.class);
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final SqsClient sqs;
    private final MessagingConfig.QueueUrls queues;
    private final MessagingProperties props;
    private final Map<String, List<MessageHandler>> handlers;
    private final Map<String, RawQueueHandler> rawHandlers;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AtomicBoolean running = new AtomicBoolean();
    private ExecutorService pollers;

    SqsWorker(SqsClient sqs, MessagingConfig.QueueUrls queues, MessagingProperties props,
              List<MessageHandler> handlers, List<RawQueueHandler> rawHandlers, JdbcClient jdbc,
              PlatformTransactionManager txManager) {
        this.sqs = sqs;
        this.queues = queues;
        this.props = props;
        this.handlers = handlers.stream().collect(Collectors.groupingBy(MessageHandler::messageType));
        this.rawHandlers = rawHandlers.stream().collect(Collectors.toMap(RawQueueHandler::queue, h -> h));
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void start() {
        if (!props.workerEnabled() || !running.compareAndSet(false, true)) {
            return;
        }
        pollers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("sqs-worker-", 0).factory());
        for (String key : queues.keys()) {
            pollers.submit(() -> poll(key));
        }
        log.info("SQS worker started for queues {}", queues.keys());
    }

    @Override
    public void stop() {
        running.set(false);
        if (pollers != null) {
            pollers.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void poll(String key) {
        while (running.get()) {
            try {
                String url = queues.url(key);
                List<Message> batch = sqs.receiveMessage(r -> r.queueUrl(url)
                        .waitTimeSeconds((int) props.pollWait().toSeconds())
                        .maxNumberOfMessages(props.maxMessages())).messages();
                for (Message m : batch) {
                    if (process(key, m.body())) {
                        sqs.deleteMessage(r -> r.queueUrl(url).receiptHandle(m.receiptHandle()));
                    }
                }
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.warn("Polling {} failed, retrying", key, e);
                sleep();
            }
        }
    }

    /** @return true if the message may be deleted */
    boolean process(String queueKey, String body) {
        try {
            RawQueueHandler raw = rawHandlers.get(queueKey);
            if (raw != null) {
                raw.handle(body);
                return true;
            }
            JsonNode node = JSON.readTree(body);
            MessageHandler.MessageEnvelope envelope = new MessageHandler.MessageEnvelope(
                    UUID.fromString(node.get("id").asString()), node.get("type").asString(),
                    UUID.fromString(node.get("tenantId").asString()), Instant.parse(node.get("occurredAt").asString()),
                    node.get("payload"));
            for (MessageHandler handler : handlers.getOrDefault(envelope.type(), List.of())) {
                TenantContext.runAs(envelope.tenantId(), () -> tx.executeWithoutResult(s -> {
                    int fresh = jdbc.sql("insert into processed_message (message_id, handler) values (?, ?) on conflict do nothing")
                            .params(envelope.id(), handler.name()).update();
                    if (fresh == 1) {
                        handler.handle(envelope);
                    }
                }));
            }
            return true;
        } catch (Exception e) {
            log.error("Message on {} failed; it will be retried (then dead-lettered)", queueKey, e);
            return false;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
