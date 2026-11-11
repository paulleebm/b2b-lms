package com.lms.shared.messaging;

/**
 * Handles messages we do not write ourselves, e.g. EventBridge events about MediaConvert jobs.
 * Same delivery rules as {@link MessageHandler}; the handler finds the tenant in the message.
 */
public interface RawQueueHandler {

    /** Key of the queue in {@code lms.messaging.queues}, e.g. "transcode". */
    String queue();

    void handle(String body);
}
