/**
 * Outbox → SQS → worker, shared by every module (docs/05 §10).
 *
 * <ul>
 *   <li>A module publishes an event that implements {@link com.lms.shared.messaging.Externalized}.</li>
 *   <li>Spring Modulith stores it in {@code event_publication} in the same transaction (the outbox).</li>
 *   <li>{@link com.lms.shared.messaging.OutboxRelay} sends it to SQS after commit and marks it done;
 *       if sending fails, it stays incomplete and is resubmitted (at-least-once).</li>
 *   <li>{@link com.lms.shared.messaging.SqsWorker} receives it and calls every
 *       {@link com.lms.shared.messaging.MessageHandler} of that type, once each
 *       ({@code processed_message}), inside the event's tenant.</li>
 * </ul>
 */
@org.springframework.modulith.NamedInterface("messaging")
package com.lms.shared.messaging;
