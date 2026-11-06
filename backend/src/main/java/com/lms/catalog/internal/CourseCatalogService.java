package com.lms.catalog.internal;

import com.lms.catalog.CourseCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the catalog for the learning module (see {@link CourseCatalog}). */
@Service
class CourseCatalogService implements CourseCatalog {

    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final LessonRepository lessons;
    private final QuizRepository quizzes;
    private final QuestionRepository questions;

    CourseCatalogService(CourseRepository courses, CourseSectionRepository sections, LessonRepository lessons,
                         QuizRepository quizzes, QuestionRepository questions) {
        this.courses = courses;
        this.sections = sections;
        this.lessons = lessons;
        this.quizzes = quizzes;
        this.questions = questions;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseOutline> assignable(UUID courseId) {
        return outline(courseId, Set.of(Course.Status.PUBLISHED));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CourseOutline> forLearner(UUID courseId) {
        return outline(courseId, Set.of(Course.Status.PUBLISHED, Course.Status.ARCHIVED));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuizKey> quizKey(UUID courseId) {
        return quizzes.findByCourseId(courseId).map(q -> {
            List<Question> rows = questions.findAllByQuizIdOrderByPosition(q.getId());
            List<KeyedQuestion> keyed = rows.stream().map(x -> new KeyedQuestion(x.getId(), x.getType().name(),
                    x.getPrompt(), QuizJson.options(x.getOptions()), QuizJson.ids(x.getAnswer()), x.getPoints())).toList();
            return new QuizKey(info(q, rows), keyed);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CourseHeader> headers(Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> lessonCounts = courses.countOutlines(courseIds).stream()
                .collect(Collectors.toMap(CourseRepository.OutlineCount::getCourseId, CourseRepository.OutlineCount::getLessons));
        Map<UUID, CourseHeader> result = new LinkedHashMap<>();
        for (Course c : courses.findAllById(courseIds)) {
            result.put(c.getId(), new CourseHeader(c.getId(), c.getTitle(), c.getDescription(), c.getStatus().name(),
                    lessonCounts.getOrDefault(c.getId(), 0L).intValue()));
        }
        return result;
    }

    private Optional<CourseOutline> outline(UUID courseId, Set<Course.Status> allowed) {
        return courses.findById(courseId).filter(c -> allowed.contains(c.getStatus())).map(c -> {
            Map<UUID, List<LessonOutline>> bySection = new LinkedHashMap<>();
            for (Lesson l : lessons.findAllByCourseIdOrderByPosition(courseId)) {
                bySection.computeIfAbsent(l.getSectionId(), k -> new ArrayList<>()).add(new LessonOutline(
                        l.getId(), l.getTitle(), l.getType().name(), l.getBody(), l.getVideoAssetId(), l.getFileId()));
            }
            List<SectionOutline> outline = sections.findAllByCourseIdOrderByPosition(courseId).stream()
                    .map(s -> new SectionOutline(s.getId(), s.getTitle(), bySection.getOrDefault(s.getId(), List.of())))
                    .toList();
            QuizInfo quiz = quizzes.findByCourseId(courseId)
                    .map(q -> info(q, questions.findAllByQuizIdOrderByPosition(q.getId())))
                    .orElse(null);
            return new CourseOutline(c.getId(), c.getTitle(), c.getDescription(), c.getStatus().name(), outline, quiz);
        });
    }

    private static QuizInfo info(Quiz q, List<Question> rows) {
        return new QuizInfo(q.getId(), q.getPassScore(), q.getMaxAttempts(), q.getTimeLimitSec(), q.getRevision(),
                rows.size(), rows.stream().mapToInt(Question::getPoints).sum());
    }
}
