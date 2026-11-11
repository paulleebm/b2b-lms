package com.lms.shared.messaging;

import tools.jackson.databind.JsonNode;

/**
 * Handles one message type on the worker. Runs inside the message's tenant and a transaction that
 * also records the message as processed by this handler, so a redelivered message is skipped.
 * Throwing leaves the message on the queue: it is retried, and after 5 attempts parked in the DLQ.
 *
 * <p>Side effects outside the database (sending a mail, writing to S3) can still happen twice if
 * the process dies between the side effect and the commit: at-least-once, by design. Handlers
 * keep such effects harmless to repeat (the same PDF overwritten, a mail at worst twice).
 */
public interface MessageHandler {

    String messageType();

    /** Stable name, part of the de-duplication key. */
    String name();

    void handle(MessageEnvelope message);

    record MessageEnvelope(java.util.UUID id, String type, java.util.UUID tenantId, java.time.Instant occurredAt,
                           JsonNode payload) {
    }
}
