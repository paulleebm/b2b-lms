package com.lms.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the learning module may read from the catalog. Read-only, tenant-scoped (Hibernate + RLS).
 * Answer keys are only in {@link QuizKey}, which never leaves the server: learning grades with it.
 */
public interface CourseCatalog {

    /** Courses that can be newly assigned: PUBLISHED only. */
    Optional<CourseOutline> assignable(UUID courseId);

    /** What an enrolled learner sees: PUBLISHED, or ARCHIVED (people already enrolled keep access). */
    Optional<CourseOutline> forLearner(UUID courseId);

    Optional<QuizKey> quizKey(UUID courseId);

    Map<UUID, CourseHeader> headers(Collection<UUID> courseIds);

    record CourseHeader(UUID id, String title, String description, String status, int lessonCount) {
    }

    record CourseOutline(UUID id, String title, String description, String status,
                         List<SectionOutline> sections, QuizInfo quiz) {

        public List<LessonOutline> lessons() {
            return sections.stream().flatMap(s -> s.lessons().stream()).toList();
        }
    }

    record SectionOutline(UUID id, String title, List<LessonOutline> lessons) {
    }

    /** {@code type}: VIDEO, DOCUMENT or TEXT. */
    record LessonOutline(UUID id, String title, String type, String body, UUID videoId, UUID documentId) {
    }

    /** @param quiz null when the course has no quiz */
    record QuizInfo(UUID id, int passScore, Integer maxAttempts, Integer timeLimitSec, int revision,
                    int questionCount, int totalPoints) {
    }

    record QuizKey(QuizInfo info, List<KeyedQuestion> questions) {
    }

    /** {@code type}: SINGLE or MULTIPLE. {@code answer}: the ids of the correct options. */
    record KeyedQuestion(UUID id, String type, String prompt, List<Option> options, List<String> answer, int points) {
    }

    record Option(String id, String text) {
    }
}
