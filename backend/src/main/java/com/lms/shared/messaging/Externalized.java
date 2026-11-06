package com.lms.shared.messaging;

import java.util.UUID;

/**
 * An event that leaves the process: stored in the outbox with the business transaction, then sent
 * to SQS for the worker. {@link #eventId()} is fixed when the event is created, so a resend after
 * a crash carries the same id and handlers can recognise it.
 */
public interface Externalized {

    UUID eventId();

    UUID tenantId();

    /** Routing key for handlers, e.g. "course.completed". */
    String messageType();
}
