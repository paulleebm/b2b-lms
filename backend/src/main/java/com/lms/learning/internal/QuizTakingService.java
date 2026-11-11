package com.lms.learning.internal;

import com.lms.catalog.CourseCatalog;
import com.lms.catalog.CourseCatalog.CourseOutline;
import com.lms.catalog.CourseCatalog.QuizKey;
import com.lms.learning.LearningEvents;
import com.lms.shared.web.ApiException;
import com.lms.shared.web.ApiNullable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Taking the course quiz (docs/05 §9). Grading happens here only; the key never leaves. */
@Service
class QuizTakingService {

    /** Network and clock slack on top of the time limit. */
    static final Duration GRACE = Duration.ofSeconds(30);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final LearnerService learner;
    private final QuizAttemptRepository attempts;
    private final LessonProgressRepository progress;
    private final CourseCatalog catalog;
    private final ApplicationEventPublisher events;

    QuizTakingService(LearnerService learner, QuizAttemptRepository attempts, LessonProgressRepository progress,
                      CourseCatalog catalog, ApplicationEventPublisher events) {
        this.learner = learner;
        this.attempts = attempts;
        this.progress = progress;
        this.catalog = catalog;
        this.events = events;
    }

    /** A question as the learner sees it: no answer key. */
    record QuestionForLearner(UUID id, String type, String prompt, List<CourseCatalog.Option> options, int points) {
    }

    record AttemptView(UUID id, int attemptNumber, @ApiNullable Integer maxAttempts, Instant startedAt,
                       @ApiNullable Instant deadlineAt, int passScore, List<QuestionForLearner> questions) {
    }

    /**
     * Starts an attempt, or returns the open one (a second click or a reload resumes instead of
     * failing). An open attempt past its deadline is closed as a zero before a new one starts.
     */
    @Transactional
    AttemptView start(UUID enrollmentId) {
        Enrollment e = learner.mine(enrollmentId);
        CourseOutline course = learner.course(e);
        QuizKey key = catalog.quizKey(course.id())
                .orElseThrow(() -> ApiException.notFound("QUIZ_NOT_FOUND", "이 코스에는 퀴즈가 없습니다."));
        if (e.getStatus() == Enrollment.Status.COMPLETED) {
            throw ApiException.conflict("ALREADY_COMPLETED", "이미 수료한 코스입니다.");
        }
        Set<UUID> done = progress.findAllByEnrollmentId(e.getId()).stream()
                .filter(LessonProgress::isCompleted).map(LessonProgress::getLessonId).collect(Collectors.toSet());
        if (!course.lessons().stream().allMatch(l -> done.contains(l.id()))) {
            throw ApiException.conflict("LESSONS_NOT_DONE", "모든 레슨을 마친 뒤에 퀴즈를 볼 수 있습니다.");
        }

        Instant now = Instant.now();
        Optional<QuizAttempt> open = attempts.findByEnrollmentIdAndStatus(e.getId(), QuizAttempt.Status.IN_PROGRESS);
        if (open.isPresent()) {
            QuizAttempt a = open.get();
            if (!expired(a, now)) {
                return view(a, key, attemptNumber(e.getId()));
            }
            close(a, QuizGrader.expired(key.questions(), Map.of()), now);
            events.publishEvent(new LearningEvents.QuizSubmitted(LearnerService.learner(e, course), a.getId(),
                    key.info().id(), 0, false, now));
        }

        long used = attempts.countByEnrollmentIdAndStatus(e.getId(), QuizAttempt.Status.SUBMITTED);
        Integer max = key.info().maxAttempts();
        if (max != null && used >= max) {
            throw ApiException.conflict("NO_ATTEMPTS_LEFT", "응시 기회를 모두 사용했습니다. 관리자에게 문의하세요.");
        }
        Instant deadline = key.info().timeLimitSec() == null ? null : now.plusSeconds(key.info().timeLimitSec());
        // The partial unique index (one IN_PROGRESS per enrollment) stops a concurrent double start.
        QuizAttempt attempt = attempts.saveAndFlush(new QuizAttempt(e.getId(), key.info().id(), key.info().revision(), now, deadline));
        int number = (int) used + 1;
        events.publishEvent(new LearningEvents.QuizAttempted(LearnerService.learner(e, course), attempt.getId(),
                key.info().id(), number, now));
        return view(attempt, key, number);
    }

    record QuestionOutcome(UUID questionId, boolean correct) {
    }

    record SubmitResult(UUID attemptId, int score, boolean passed, int passScore, boolean expired,
                        List<QuestionOutcome> questions, @ApiNullable Integer attemptsLeft,
                        boolean courseCompleted, @ApiNullable String certificateSerial) {
    }

    @Transactional
    SubmitResult submit(UUID enrollmentId, UUID attemptId, Map<UUID, List<String>> answers) {
        Enrollment e = learner.mine(enrollmentId);
        CourseOutline course = learner.course(e);
        QuizAttempt a = attempts.findById(attemptId).filter(x -> x.getEnrollmentId().equals(e.getId()))
                .orElseThrow(() -> ApiException.notFound("ATTEMPT_NOT_FOUND", "응시 기록을 찾을 수 없습니다."));
        QuizKey key = catalog.quizKey(course.id())
                .orElseThrow(() -> ApiException.notFound("QUIZ_NOT_FOUND", "이 코스에는 퀴즈가 없습니다."));
        if (a.getStatus() != QuizAttempt.Status.IN_PROGRESS) {
            throw ApiException.conflict("ATTEMPT_ALREADY_SUBMITTED", "이미 제출한 응시입니다.");
        }
        // The quiz cannot change while people are enrolled (the course cannot be unpublished),
        // so the revision recorded at start is the one being graded.
        Instant now = Instant.now();
        boolean late = expired(a, now);
        QuizGrader.Result result = late
                ? QuizGrader.expired(key.questions(), answers)
                : QuizGrader.grade(key.questions(), answers, key.info().passScore());
        if (!close(a, result, now)) {
            throw ApiException.conflict("ATTEMPT_ALREADY_SUBMITTED", "이미 제출한 응시입니다.");
        }
        events.publishEvent(new LearningEvents.QuizSubmitted(LearnerService.learner(e, course), a.getId(),
                key.info().id(), result.score(), result.passed(), now));

        String serial = result.passed() ? learner.completeCourse(e, course, now) : null;
        long used = attempts.countByEnrollmentIdAndStatus(e.getId(), QuizAttempt.Status.SUBMITTED);
        Integer max = key.info().maxAttempts();
        return new SubmitResult(a.getId(), result.score(), result.passed(), key.info().passScore(), late,
                result.questions().stream().map(q -> new QuestionOutcome(q.questionId(), q.correct())).toList(),
                max == null ? null : (int) Math.max(0, max - used), serial != null, serial);
    }

    /** Conditional UPDATE: false when another request submitted first. */
    private boolean close(QuizAttempt a, QuizGrader.Result result, Instant now) {
        return attempts.submitIfOpen(a.getId(), JSON.writeValueAsString(result.questions()),
                result.score(), result.passed(), now) == 1;
    }

    private static boolean expired(QuizAttempt a, Instant now) {
        return a.getDeadlineAt() != null && now.isAfter(a.getDeadlineAt().plus(GRACE));
    }

    private int attemptNumber(UUID enrollmentId) {
        return (int) attempts.countByEnrollmentIdAndStatus(enrollmentId, QuizAttempt.Status.SUBMITTED) + 1;
    }

    private static AttemptView view(QuizAttempt a, QuizKey key, int number) {
        return new AttemptView(a.getId(), number, key.info().maxAttempts(), a.getStartedAt(), a.getDeadlineAt(),
                key.info().passScore(), key.questions().stream()
                        .map(q -> new QuestionForLearner(q.id(), q.type(), q.prompt(), q.options(), q.points())).toList());
    }
}
