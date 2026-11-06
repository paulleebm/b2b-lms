package com.lms.notification.internal;

import com.lms.learning.LearningEvents;
import com.lms.shared.messaging.MessageHandler;
import com.lms.tenancy.MemberDirectory;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mail handlers on the worker. Each handles one outbox message type, once per message
 * ({@code processed_message}). People who are no longer active get nothing.
 */
final class MailHandlers {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("M월 d일").withZone(ZoneId.of("Asia/Seoul"));

    private MailHandlers() {
    }

    static String due(Instant dueAt) {
        return dueAt == null ? null : DUE.format(dueAt);
    }
}

@Component
class AssignmentMailHandler implements MessageHandler {

    private final Mailer mailer;
    private final MemberDirectory members;

    AssignmentMailHandler(Mailer mailer, MemberDirectory members) {
        this.mailer = mailer;
        this.members = members;
    }

    @Override
    public String messageType() {
        return LearningEvents.EnrollmentsAssigned.TYPE;
    }

    @Override
    public String name() {
        return "mail-assigned";
    }

    @Override
    public void handle(MessageEnvelope message) {
        var event = MailHandlers.JSON.treeToValue(message.payload(), LearningEvents.EnrollmentsAssigned.class);
        Map<UUID, MemberDirectory.Contact> contacts = members.activeContacts(event.membershipIds());
        for (MemberDirectory.Contact c : contacts.values()) {
            mailer.send(c.email(), "[필수 교육] " + event.courseTitle(), "assigned", Map.of(
                    "name", c.name(), "courseTitle", event.courseTitle(),
                    "due", event.dueAt() == null ? "" : MailHandlers.due(event.dueAt())));
        }
    }
}

@Component
class ReminderMailHandler implements MessageHandler {

    private final Mailer mailer;
    private final MemberDirectory members;

    ReminderMailHandler(Mailer mailer, MemberDirectory members) {
        this.mailer = mailer;
        this.members = members;
    }

    @Override
    public String messageType() {
        return LearningEvents.RemindersRequested.TYPE;
    }

    @Override
    public String name() {
        return "mail-reminder";
    }

    @Override
    public void handle(MessageEnvelope message) {
        var event = MailHandlers.JSON.treeToValue(message.payload(), LearningEvents.RemindersRequested.class);
        Map<UUID, MemberDirectory.Contact> contacts = members.activeContacts(
                event.reminders().stream().map(LearningEvents.Reminder::membershipId).toList());
        for (LearningEvents.Reminder r : event.reminders()) {
            MemberDirectory.Contact c = contacts.get(r.membershipId());
            if (c == null) {
                continue;
            }
            String subject = (r.overdue() ? "[마감 지남] " : "[마감 임박] ") + r.courseTitle();
            mailer.send(c.email(), subject, "reminder", Map.of(
                    "name", c.name(), "courseTitle", r.courseTitle(), "overdue", r.overdue(),
                    "due", r.dueAt() == null ? "" : MailHandlers.due(r.dueAt()),
                    "path", "/app/learn/" + r.enrollmentId()));
        }
    }
}

@Component
class CompletionMailHandler implements MessageHandler {

    private final Mailer mailer;
    private final MemberDirectory members;

    CompletionMailHandler(Mailer mailer, MemberDirectory members) {
        this.mailer = mailer;
        this.members = members;
    }

    @Override
    public String messageType() {
        return LearningEvents.CourseCompleted.TYPE;
    }

    @Override
    public String name() {
        return "mail-completed";
    }

    @Override
    public void handle(MessageEnvelope message) {
        var event = MailHandlers.JSON.treeToValue(message.payload(), LearningEvents.CourseCompleted.class);
        MemberDirectory.Contact c = members.activeContacts(List.of(event.membershipId())).get(event.membershipId());
        if (c == null) {
            return;
        }
        mailer.send(c.email(), "[수료] " + event.learner().courseTitle(), "completed", Map.of(
                "name", c.name(), "courseTitle", event.learner().courseTitle(),
                "serial", event.certificateSerial()));
    }
}
