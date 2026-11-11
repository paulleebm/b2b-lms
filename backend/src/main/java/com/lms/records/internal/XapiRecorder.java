package com.lms.records.internal;

import com.lms.learning.LearningEvents;
import com.lms.learning.LearningEvents.Learner;
import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Learning events → xAPI 1.0.3 statements in {@code learning_record}.
 *
 * <p>Why xAPI: it is the e-learning industry's interchange format for "who did what" (actor ·
 * verb · object · result · context). Storing statements in that shape means the history can be
 * exported to any external LRS or analytics tool without translation, and the verbs are standard
 * ADL / cmi5 IRIs rather than our own vocabulary.
 *
 * <p>Synchronous listener = same transaction as the learning change: a rolled-back completion
 * leaves no "completed" statement. Statement ids are deterministic for events that must not be
 * duplicated (progress steps, completions); a retry collides on the primary key and is ignored.
 */
@Component
class XapiRecorder {

    static final String HOME = "https://lms.example";
    static final String ADL = "http://adlnet.gov/expapi/verbs/";
    static final String ACTIVITY_TYPES = "http://adlnet.gov/expapi/activities/";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    XapiRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void on(LearningEvents.LessonLaunched e) {
        record(UuidV7.generate(), e.learner(), verb("launched", "시작함"), lesson(e.learner(), e.lessonId(), e.lessonTitle()),
                null, e.at());
    }

    @EventListener
    void on(LearningEvents.LessonProgressed e) {
        Map<String, Object> result = Map.of("extensions",
                Map.of("https://w3id.org/xapi/cmi5/result/extensions/progress", e.percent()));
        record(stableId("progressed", e.learner().enrollmentId(), e.lessonId(), e.percent()), e.learner(),
                verb("progressed", "진행함"), lesson(e.learner(), e.lessonId(), e.lessonTitle()), result, e.at());
    }

    @EventListener
    void on(LearningEvents.LessonCompleted e) {
        record(stableId("completed", e.learner().enrollmentId(), e.lessonId(), 0), e.learner(),
                verb("completed", "완료함"), lesson(e.learner(), e.lessonId(), e.lessonTitle()),
                Map.of("completion", true), e.at());
    }

    @EventListener
    void on(LearningEvents.QuizAttempted e) {
        record(stableId("attempted", e.learner().enrollmentId(), e.attemptId(), 0), e.learner(),
                verb("attempted", "응시함"), quiz(e.learner(), e.quizId()),
                Map.of("extensions", Map.of(HOME + "/xapi/extensions/attempt-number", e.attemptNumber())), e.at());
    }

    @EventListener
    void on(LearningEvents.QuizSubmitted e) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", Map.of("scaled", e.score() / 100.0, "raw", e.score(), "min", 0, "max", 100));
        result.put("success", e.passed());
        result.put("completion", true);
        record(stableId(e.passed() ? "passed" : "failed", e.learner().enrollmentId(), e.attemptId(), 0), e.learner(),
                verb(e.passed() ? "passed" : "failed", e.passed() ? "합격함" : "불합격함"), quiz(e.learner(), e.quizId()),
                result, e.at());
    }

    @EventListener
    void on(LearningEvents.CourseCompleted e) {
        Map<String, Object> result = Map.of("completion", true,
                "extensions", Map.of(HOME + "/xapi/extensions/certificate-serial", e.certificateSerial()));
        record(stableId("course-completed", e.learner().enrollmentId(), e.learner().courseId(), 0), e.learner(),
                verb("completed", "완료함"), course(e.learner()), result, e.at());
    }

    // --- statement building -----------------------------------------------------------------------

    private void record(UUID id, Learner who, Map<String, Object> verb, Map<String, Object> object,
                        Map<String, Object> result, Instant at) {
        Map<String, Object> statement = new LinkedHashMap<>();
        statement.put("id", id.toString());
        statement.put("actor", Map.of("objectType", "Agent",
                "account", Map.of("homePage", HOME, "name", who.userId().toString())));
        statement.put("verb", verb);
        statement.put("object", object);
        if (result != null) {
            statement.put("result", result);
        }
        statement.put("context", Map.of(
                "registration", who.enrollmentId().toString(),
                "platform", "LMS",
                "language", "ko-KR",
                "contextActivities", Map.of("parent", java.util.List.of(course(who)))));
        statement.put("timestamp", at.toString());
        statement.put("version", "1.0.3");

        jdbc.sql("""
                        insert into learning_record (id, tenant_id, enrollment_id, actor_user_id, verb_iri, object_iri,
                                                     result, statement, "timestamp")
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)
                        on conflict (id) do nothing
                        """)
                .params(id, TenantContext.requireTenantId(), who.enrollmentId(), who.userId(), verb.get("id"),
                        object.get("id"), result == null ? null : JSON.writeValueAsString(result),
                        JSON.writeValueAsString(statement), Timestamp.from(at))
                .update();
    }

    private static Map<String, Object> verb(String name, String korean) {
        return Map.of("id", ADL + name, "display", Map.of("ko-KR", korean, "en-US", name));
    }

    private static Map<String, Object> lesson(Learner who, UUID lessonId, String title) {
        return activity(HOME + "/xapi/activities/courses/" + who.courseId() + "/lessons/" + lessonId, title, "lesson");
    }

    private static Map<String, Object> quiz(Learner who, UUID quizId) {
        return activity(HOME + "/xapi/activities/courses/" + who.courseId() + "/quiz/" + quizId,
                who.courseTitle() + " 퀴즈", "assessment");
    }

    private static Map<String, Object> course(Learner who) {
        return activity(HOME + "/xapi/activities/courses/" + who.courseId(), who.courseTitle(), "course");
    }

    private static Map<String, Object> activity(String iri, String name, String type) {
        return Map.of("objectType", "Activity", "id", iri,
                "definition", Map.of("name", Map.of("ko-KR", name), "type", ACTIVITY_TYPES + type));
    }

    /** Same event → same statement id (UUID v3 of its identity), so re-recording is a no-op. */
    private static UUID stableId(String kind, UUID registration, UUID target, int detail) {
        return UUID.nameUUIDFromBytes((kind + ":" + registration + ":" + target + ":" + detail)
                .getBytes(StandardCharsets.UTF_8));
    }
}
