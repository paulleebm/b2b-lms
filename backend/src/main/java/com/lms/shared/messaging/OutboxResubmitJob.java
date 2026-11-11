package com.lms.shared.messaging;

import java.time.Duration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Re-sends outbox events whose relay failed (e.g. SQS was unreachable). Runs on one instance
 * at a time (ShedLock). Combined with at-least-once handlers, nothing is lost and nothing breaks
 * if something is sent twice.
 */
@Component
class OutboxResubmitJob {

    private final IncompleteEventPublications incomplete;

    OutboxResubmitJob(IncompleteEventPublications incomplete) {
        this.incomplete = incomplete;
    }

    @Scheduled(fixedDelayString = "${lms.outbox.resubmit-every:PT1M}", initialDelayString = "PT30S")
    @SchedulerLock(name = "outbox-resubmit", lockAtMostFor = "PT5M")
    void resubmit() {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(1));
    }
}
