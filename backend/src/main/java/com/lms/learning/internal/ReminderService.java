package com.lms.learning.internal;

import com.lms.catalog.CourseCatalog;
import com.lms.learning.LearningEvents;
import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import com.lms.shared.web.ApiNullable;
import com.lms.tenancy.Companies;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reminder mails for unfinished courses, from the dashboard button or the daily job.
 * Throttled in the database: one UPDATE claims the enrollments not reminded in the last 20 hours
 * and stamps them, so pressing the button twice (or the job and the button together) sends once.
 */
@Service
class ReminderService {

    private final JdbcClient jdbc;
    private final CourseCatalog catalog;
    private final ApplicationEventPublisher events;

    ReminderService(JdbcClient jdbc, CourseCatalog catalog, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.events = events;
    }

    record ReminderResult(int queued) {
    }

    record Claimed(UUID enrollmentId, UUID membershipId, UUID courseId, Instant dueAt) {
    }

    /**
     * @param courseId     only this course (null = all)
     * @param dueSoonOnly  only overdue or due within 3 days (the daily job); the button reminds everyone unfinished
     */
    @Transactional
    ReminderResult remind(@ApiNullable UUID courseId, boolean dueSoonOnly) {
        return remind(courseId, null, dueSoonOnly);
    }

    /** @param enrollmentIds only these (null = everyone matching) */
    @Transactional
    ReminderResult remind(@ApiNullable UUID courseId, @ApiNullable List<UUID> enrollmentIds, boolean dueSoonOnly) {
        String ids = enrollmentIds == null ? null
                : "{" + String.join(",", enrollmentIds.stream().map(UUID::toString).toList()) + "}";
        List<Claimed> claimed = jdbc.sql("""
                        update enrollment set reminded_at = now()
                        where status <> 'COMPLETED'
                          and (reminded_at is null or reminded_at < now() - interval '20 hours')
                          and (cast(? as uuid) is null or course_id = cast(? as uuid))
                          and (cast(? as uuid[]) is null or id = any(cast(? as uuid[])))
                          and (? = false or (due_at is not null and due_at < now() + interval '3 days'))
                        returning id, membership_id, course_id, due_at
                        """)
                .params(courseId, courseId, ids, ids, dueSoonOnly)
                .query((rs, n) -> new Claimed(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class), toInstant(rs.getTimestamp(4))))
                .list();
        if (claimed.isEmpty()) {
            return new ReminderResult(0);
        }
        Map<UUID, CourseCatalog.CourseHeader> courses = catalog.headers(
                claimed.stream().map(Claimed::courseId).collect(Collectors.toSet()));
        Instant now = Instant.now();
        List<LearningEvents.Reminder> reminders = claimed.stream()
                .map(c -> new LearningEvents.Reminder(c.enrollmentId(), c.membershipId(), c.courseId(),
                        courses.containsKey(c.courseId()) ? courses.get(c.courseId()).title() : "",
                        c.dueAt(), c.dueAt() != null && c.dueAt().isBefore(now)))
                .toList();
        events.publishEvent(new LearningEvents.RemindersRequested(UuidV7.generate(), TenantContext.requireTenantId(),
                reminders, TenantContext.membershipId().orElse(null)));
        return new ReminderResult(reminders.size());
    }

    private static Instant toInstant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}

@RestController
@PreAuthorize("hasRole('ORG_ADMIN')")
class ReminderController {

    private final ReminderService reminders;

    ReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    /**
     * The dashboard's reminder buttons: one row ({@code enrollmentIds}), one course, or everyone
     * unfinished. At most one reminder per enrollment per day, whatever is pressed.
     */
    @PostMapping("/v1/reminders")
    ReminderService.ReminderResult remind(@RequestBody(required = false) RemindRequest request) {
        if (request == null) {
            return reminders.remind(null, null, false);
        }
        return reminders.remind(request.courseId(), request.enrollmentIds(), false);
    }

    record RemindRequest(@ApiNullable UUID courseId,
                         @ApiNullable @jakarta.validation.constraints.Size(max = 500) List<UUID> enrollmentIds) {
    }
}

/** Every morning: remind people whose deadline is within three days or already passed. One instance only (ShedLock). */
@Component
class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final ReminderService reminders;
    private final Companies companies;

    ReminderJob(ReminderService reminders, Companies companies) {
        this.reminders = reminders;
        this.companies = companies;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "daily-reminders", lockAtMostFor = "PT30M")
    void run() {
        for (UUID tenantId : companies.allIds()) {
            try {
                int queued = TenantContext.callAs(tenantId, () -> reminders.remind(null, true).queued());
                if (queued > 0) {
                    log.info("Queued {} reminder(s) for tenant {}", queued, tenantId);
                }
            } catch (RuntimeException e) {
                log.error("Reminders failed for tenant {}", tenantId, e);   // one company's failure must not stop the others
            }
        }
    }
}
