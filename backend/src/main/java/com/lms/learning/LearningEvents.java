package com.lms.learning;

import com.lms.shared.messaging.Externalized;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What happened while someone learned. Published inside the learning transaction; the records
 * module turns each one into an xAPI statement in that same transaction (so a rolled-back
 * completion never leaves a "completed" statement behind). Learning does not know who listens.
 *
 * <p>Events marked {@link Externalized} also go through the outbox to SQS, where the worker
 * renders certificates and sends mail — work that must not slow down or fail the learner's request.
 */
public final class LearningEvents {

    private LearningEvents() {
    }

    /** Who, in which enrollment (xAPI context.registration), on which course. */
    public record Learner(UUID tenantId, UUID enrollmentId, UUID userId, UUID courseId, String courseTitle) {
    }

    public record LessonLaunched(Learner learner, UUID lessonId, String lessonTitle, Instant at) {
    }

    /** Crossed 25 / 50 / 75 percent of the lesson's credited watch time. */
    public record LessonProgressed(Learner learner, UUID lessonId, String lessonTitle, int percent, Instant at) {
    }

    public record LessonCompleted(Learner learner, UUID lessonId, String lessonTitle, Instant at) {
    }

    public record QuizAttempted(Learner learner, UUID attemptId, UUID quizId, int attemptNumber, Instant at) {
    }

    /** @param score percent 0–100 */
    public record QuizSubmitted(Learner learner, UUID attemptId, UUID quizId, int score, boolean passed, Instant at) {
    }

    /** Worker: render the certificate PDF, mail the learner. */
    public record CourseCompleted(UUID eventId, Learner learner, UUID membershipId, String certificateSerial, Instant at)
            implements Externalized {

        public static final String TYPE = "course.completed";

        @Override
        public UUID tenantId() {
            return learner.tenantId();
        }

        @Override
        public String messageType() {
            return TYPE;
        }
    }

    /** Worker: tell each newly enrolled person (assignment fan-out or new-hire sync). */
    public record EnrollmentsAssigned(UUID eventId, UUID tenantId, UUID courseId, String courseTitle, Instant dueAt,
                                      List<UUID> membershipIds, UUID assignedBy) implements Externalized {

        public static final String TYPE = "enrollments.assigned";

        @Override
        public String messageType() {
            return TYPE;
        }
    }

    public record Reminder(UUID enrollmentId, UUID membershipId, UUID courseId, String courseTitle, Instant dueAt,
                           boolean overdue) {
    }

    /** Worker: one reminder mail per enrollment. Already throttled (at most one per enrollment per day). */
    public record RemindersRequested(UUID eventId, UUID tenantId, List<Reminder> reminders, UUID requestedBy)
            implements Externalized {

        public static final String TYPE = "reminders.requested";

        @Override
        public String messageType() {
            return TYPE;
        }
    }
}
