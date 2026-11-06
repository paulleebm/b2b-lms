package com.lms.learning.internal;

import com.lms.catalog.CourseCatalog;
import com.lms.catalog.CourseCatalog.CourseOutline;
import com.lms.catalog.CourseCatalog.LessonOutline;
import com.lms.files.MediaLibrary;
import com.lms.learning.LearningEvents;
import com.lms.shared.TenantContext;
import com.lms.shared.web.ApiException;
import com.lms.shared.web.ApiNullable;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The learner's side: "my courses", a course's outline with my progress, opening a lesson,
 * video heartbeats, finishing text/document lessons, and course completion.
 *
 * <p>Every lookup goes through {@link #mine(UUID)}: an enrollment that is not the caller's
 * (another person's, or another company's) is a 404, never a 403.
 */
@Service
class LearnerService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private final EnrollmentRepository enrollments;
    private final LessonProgressRepository progress;
    private final CertificateRepository certificates;
    private final QuizAttemptRepository attempts;
    private final EnrollmentWriter writer;
    private final CourseCatalog catalog;
    private final MediaLibrary media;
    private final ApplicationEventPublisher events;

    LearnerService(EnrollmentRepository enrollments, LessonProgressRepository progress,
                   CertificateRepository certificates, QuizAttemptRepository attempts, EnrollmentWriter writer,
                   CourseCatalog catalog, MediaLibrary media, ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.progress = progress;
        this.certificates = certificates;
        this.attempts = attempts;
        this.writer = writer;
        this.catalog = catalog;
        this.media = media;
        this.events = events;
    }

    // --- views --------------------------------------------------------------------------------

    record LessonRef(UUID id, String title) {
    }

    record MyEnrollment(UUID id, UUID courseId, String title, String description, Enrollment.Status status,
                        @ApiNullable Instant dueAt, boolean overdue, int lessonsDone, int lessonsTotal,
                        @ApiNullable LessonRef nextLesson, @ApiNullable Instant completedAt,
                        @ApiNullable String certificateSerial) {
    }

    record LessonState(UUID id, String title, String type, boolean completed, @ApiNullable Integer percent) {
    }

    record SectionState(UUID id, String title, List<LessonState> lessons) {
    }

    /**
     * @param unlocked every lesson is complete, so the quiz may be taken
     * @param bestScore highest submitted score so far
     */
    record QuizState(int passScore, @ApiNullable Integer maxAttempts, @ApiNullable Integer timeLimitSec,
                     int questionCount, int attemptsUsed, @ApiNullable Integer bestScore, boolean passed,
                     boolean unlocked, @ApiNullable UUID openAttemptId) {
    }

    record EnrollmentDetail(UUID id, UUID courseId, String title, String description, Enrollment.Status status,
                            @ApiNullable Instant dueAt, boolean overdue, @ApiNullable Instant completedAt,
                            @ApiNullable String certificateSerial, int lessonsDone, int lessonsTotal,
                            @ApiNullable LessonRef nextLesson, List<SectionState> sections, @ApiNullable QuizState quiz) {
    }

    // --- queries ------------------------------------------------------------------------------

    /** Open courses first (earliest due date first, undated last), then completed ones, newest first. */
    @Transactional(readOnly = true)
    List<MyEnrollment> myEnrollments() {
        List<Enrollment> mine = enrollments.findAllByMembershipId(TenantContext.requireMembershipId());
        if (mine.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        Set<UUID> ids = mine.stream().map(Enrollment::getId).collect(Collectors.toSet());
        Map<UUID, List<LessonProgress>> progressByEnrollment = progress.findAllByEnrollmentIdIn(ids).stream()
                .collect(Collectors.groupingBy(LessonProgress::getEnrollmentId));
        Map<UUID, String> serials = certificates.findAllByEnrollmentIdIn(ids).stream()
                .collect(Collectors.toMap(Certificate::getEnrollmentId, Certificate::getSerial));

        return mine.stream()
                .map(e -> {
                    CourseOutline course = catalog.forLearner(e.getCourseId()).orElse(null);
                    if (course == null) {
                        return null;   // course withdrawn back to draft before anyone started: hide it
                    }
                    Set<UUID> done = completedLessons(progressByEnrollment.getOrDefault(e.getId(), List.of()));
                    List<LessonOutline> lessons = course.lessons();
                    return new MyEnrollment(e.getId(), course.id(), course.title(), course.description(), e.getStatus(),
                            e.getDueAt(), e.isOverdue(now), (int) lessons.stream().filter(l -> done.contains(l.id())).count(),
                            lessons.size(), next(lessons, done), e.getCompletedAt(), serials.get(e.getId()));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing((MyEnrollment m) -> m.status() == Enrollment.Status.COMPLETED)
                        .thenComparing(m -> m.status() == Enrollment.Status.COMPLETED ? null : m.dueAt(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MyEnrollment::completedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    EnrollmentDetail detail(UUID enrollmentId) {
        Enrollment e = mine(enrollmentId);
        CourseOutline course = course(e);
        Map<UUID, LessonProgress> byLesson = progress.findAllByEnrollmentId(e.getId()).stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, Function.identity()));
        Set<UUID> done = completedLessons(byLesson.values());

        Map<UUID, Integer> durations = media.videos(course.lessons().stream()
                        .map(LessonOutline::videoId).filter(java.util.Objects::nonNull).toList())
                .entrySet().stream().filter(x -> x.getValue().durationSeconds() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, x -> x.getValue().durationSeconds()));

        List<SectionState> sections = course.sections().stream().map(s -> new SectionState(s.id(), s.title(),
                s.lessons().stream().map(l -> {
                    LessonProgress p = byLesson.get(l.id());
                    Integer percent = null;
                    if ("VIDEO".equals(l.type()) && p != null && durations.containsKey(l.videoId())) {
                        percent = (int) Math.min(100, Math.round(p.getWatchedSec() * 100.0 / durations.get(l.videoId())));
                    }
                    return new LessonState(l.id(), l.title(), l.type(), done.contains(l.id()), percent);
                }).toList())).toList();

        QuizState quiz = null;
        if (course.quiz() != null) {
            List<QuizAttempt> tries = attempts.findAllByEnrollmentIdOrderByStartedAt(e.getId());
            var submitted = tries.stream().filter(a -> a.getStatus() == QuizAttempt.Status.SUBMITTED).toList();
            UUID open = tries.stream().filter(a -> a.getStatus() == QuizAttempt.Status.IN_PROGRESS)
                    .map(QuizAttempt::getId).findFirst().orElse(null);
            quiz = new QuizState(course.quiz().passScore(), course.quiz().maxAttempts(), course.quiz().timeLimitSec(),
                    course.quiz().questionCount(), submitted.size(),
                    submitted.stream().map(QuizAttempt::getScore).filter(java.util.Objects::nonNull).max(Integer::compare).orElse(null),
                    submitted.stream().anyMatch(a -> Boolean.TRUE.equals(a.getPassed())),
                    done.size() == course.lessons().size(), open);
        }
        String serial = certificates.findByEnrollmentId(e.getId()).map(Certificate::getSerial).orElse(null);
        List<LessonOutline> lessons = course.lessons();
        return new EnrollmentDetail(e.getId(), course.id(), course.title(), course.description(), e.getStatus(),
                e.getDueAt(), e.isOverdue(Instant.now()), e.getCompletedAt(), serial,
                (int) lessons.stream().filter(l -> done.contains(l.id())).count(), lessons.size(),
                next(lessons, done), sections, quiz);
    }

    // --- opening a lesson ---------------------------------------------------------------------

    record VideoContent(String url, String format, @ApiNullable Integer durationSeconds,
                        @com.fasterxml.jackson.annotation.JsonIgnore
                        @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
                        List<MediaLibrary.SignedCookie> cookies) {
    }

    record DocumentContent(String url, String fileName, String contentType, long sizeBytes) {
    }

    record LessonContent(UUID id, String title, String type, @ApiNullable String body,
                         @ApiNullable VideoContent video, @ApiNullable DocumentContent document,
                         int lastPositionSec, int watchedSec, boolean completed,
                         @ApiNullable LessonRef previous, @ApiNullable LessonRef next) {
    }

    /** POST, not GET: it has effects (enrollment starts, a playback session starts, xAPI 'launched'). */
    @Transactional
    LessonContent launch(UUID enrollmentId, UUID lessonId) {
        Enrollment e = mine(enrollmentId);
        CourseOutline course = course(e);
        List<LessonOutline> lessons = course.lessons();
        int index = indexOf(lessons, lessonId);
        LessonOutline lesson = lessons.get(index);

        Instant now = Instant.now();
        e.start(now);
        writer.ensureProgress(e.getId(), lessonId);
        LessonProgress p = progress.lock(e.getId(), lessonId).orElseThrow();
        p.launched(now);

        VideoContent video = null;
        DocumentContent document = null;
        if ("VIDEO".equals(lesson.type())) {
            video = media.playback(lesson.videoId())
                    .map(v -> new VideoContent(v.url(), v.format(), v.durationSeconds(), v.cookies()))
                    .orElseThrow(() -> ApiException.notFound("VIDEO_NOT_READY", "영상을 재생할 수 없습니다. 잠시 후 다시 시도하세요."));
        } else if ("DOCUMENT".equals(lesson.type())) {
            document = media.documentLink(lesson.documentId())
                    .map(d -> new DocumentContent(d.url(), d.fileName(), d.contentType(), d.sizeBytes()))
                    .orElseThrow(() -> ApiException.notFound("FILE_NOT_FOUND", "문서를 찾을 수 없습니다."));
        }
        events.publishEvent(new LearningEvents.LessonLaunched(learner(e, course), lessonId, lesson.title(), now));

        return new LessonContent(lesson.id(), lesson.title(), lesson.type(), lesson.body(), video, document,
                p.getLastPositionSec(), p.getWatchedSec(), p.isCompleted(),
                index > 0 ? ref(lessons.get(index - 1)) : null,
                index < lessons.size() - 1 ? ref(lessons.get(index + 1)) : null);
    }

    // --- progress -----------------------------------------------------------------------------

    /**
     * @param percent credited watch time against the server's duration (videos), else null
     * @param courseCompleted this call completed the whole course (no quiz, last lesson)
     */
    record ProgressResult(int watchedSec, @ApiNullable Integer durationSeconds, @ApiNullable Integer percent,
                          boolean completed, boolean courseCompleted, @ApiNullable String certificateSerial) {
    }

    /** Video heartbeat, every ~15 s while playing (and on pause / end). */
    @Transactional
    ProgressResult heartbeat(UUID enrollmentId, UUID lessonId, int positionSec) {
        Enrollment e = mine(enrollmentId);
        CourseOutline course = course(e);
        LessonOutline lesson = course.lessons().get(indexOf(course.lessons(), lessonId));
        if (!"VIDEO".equals(lesson.type())) {
            throw ApiException.badRequest("NOT_A_VIDEO", "영상 레슨만 시청 기록을 보냅니다.");
        }
        LessonProgress p = progress.lock(e.getId(), lessonId)
                .orElseThrow(() -> ApiException.conflict("LESSON_NOT_OPENED", "레슨을 먼저 열어야 합니다."));
        Integer duration = media.videos(List.of(lesson.videoId())).values().stream()
                .findFirst().map(MediaLibrary.VideoSummary::durationSeconds).orElse(null);
        if (duration == null || duration <= 0) {
            throw ApiException.unprocessable("DURATION_UNKNOWN", "영상 길이를 아직 알 수 없어 진도를 기록하지 못했습니다.");
        }

        Instant now = Instant.now();
        int stepBefore = p.getProgressedStep();
        ProgressRules.Outcome outcome = ProgressRules.heartbeat(p.getWatchedSec(), p.getLastPositionSec(),
                p.getLastHeartbeatAt(), positionSec, now, duration);
        p.apply(outcome, now);

        LearningEvents.Learner who = learner(e, course);
        // xAPI 'progressed' only when a new 25 % step is crossed, not on every heartbeat.
        for (int step = stepBefore + 1; step <= Math.min(outcome.step(), 3); step++) {
            events.publishEvent(new LearningEvents.LessonProgressed(who, lessonId, lesson.title(), step * 25, now));
        }
        CompletionOutcome completion = outcome.complete() ? completeLesson(e, course, p, lesson, now) : CompletionOutcome.NONE;
        return new ProgressResult(p.getWatchedSec(), duration, (int) Math.min(100, Math.round(p.getWatchedSec() * 100.0 / duration)),
                p.isCompleted(), completion.courseCompleted(), completion.serial());
    }

    /** Text and document lessons: done when the learner says so (after opening it). */
    @Transactional
    ProgressResult markDone(UUID enrollmentId, UUID lessonId) {
        Enrollment e = mine(enrollmentId);
        CourseOutline course = course(e);
        LessonOutline lesson = course.lessons().get(indexOf(course.lessons(), lessonId));
        if ("VIDEO".equals(lesson.type())) {
            throw ApiException.badRequest("VIDEO_NEEDS_WATCHING", "영상 레슨은 시청 시간으로 완료됩니다.");
        }
        LessonProgress p = progress.lock(e.getId(), lessonId)
                .orElseThrow(() -> ApiException.conflict("LESSON_NOT_OPENED", "레슨을 먼저 열어야 합니다."));
        CompletionOutcome completion = completeLesson(e, course, p, lesson, Instant.now());
        return new ProgressResult(0, null, null, true, completion.courseCompleted(), completion.serial());
    }

    record CompletionOutcome(boolean courseCompleted, String serial) {
        static final CompletionOutcome NONE = new CompletionOutcome(false, null);
    }

    private CompletionOutcome completeLesson(Enrollment e, CourseOutline course, LessonProgress p,
                                             LessonOutline lesson, Instant now) {
        if (!p.complete(now)) {
            return CompletionOutcome.NONE;
        }
        events.publishEvent(new LearningEvents.LessonCompleted(learner(e, course), lesson.id(), lesson.title(), now));
        progress.flush();
        Set<UUID> done = completedLessons(progress.findAllByEnrollmentId(e.getId()));
        boolean allDone = course.lessons().stream().allMatch(l -> done.contains(l.id()));
        if (allDone && course.quiz() == null) {
            // No quiz: finishing the last lesson completes the course.
            String serial = completeCourse(e, course, now);
            return new CompletionOutcome(serial != null, serial);
        }
        return CompletionOutcome.NONE;
    }

    /**
     * Completion, the certificate row and the event — one transaction (docs/05 §9): a completed
     * enrollment without a certificate cannot exist. The conditional UPDATE makes it exactly once.
     *
     * @return the certificate serial, or null if it was already completed
     */
    String completeCourse(Enrollment e, CourseOutline course, Instant now) {
        if (enrollments.completeIfNot(e.getId(), now) == 0) {
            return null;
        }
        String serial = "LMS-" + Year.now(ZoneId.of("Asia/Seoul")) + "-" + randomCode(8);
        certificates.save(new Certificate(e.getId(), serial, now));
        events.publishEvent(new LearningEvents.CourseCompleted(com.lms.shared.UuidV7.generate(), learner(e, course),
                e.getMembershipId(), serial, now));
        return serial;
    }

    // --- helpers ------------------------------------------------------------------------------

    Enrollment mine(UUID enrollmentId) {
        return enrollments.findByIdAndMembershipId(enrollmentId, TenantContext.requireMembershipId())
                .orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "수강 정보를 찾을 수 없습니다."));
    }

    CourseOutline course(Enrollment e) {
        return catalog.forLearner(e.getCourseId())
                .orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "코스를 찾을 수 없습니다."));
    }

    static LearningEvents.Learner learner(Enrollment e, CourseOutline course) {
        return new LearningEvents.Learner(e.getTenantId(), e.getId(), TenantContext.userId().orElseThrow(),
                course.id(), course.title());
    }

    private static int indexOf(List<LessonOutline> lessons, UUID lessonId) {
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).id().equals(lessonId)) {
                return i;
            }
        }
        throw ApiException.notFound("LESSON_NOT_FOUND", "레슨을 찾을 수 없습니다.");
    }

    private static Set<UUID> completedLessons(java.util.Collection<LessonProgress> rows) {
        return rows.stream().filter(LessonProgress::isCompleted).map(LessonProgress::getLessonId).collect(Collectors.toSet());
    }

    private static LessonRef next(List<LessonOutline> lessons, Set<UUID> done) {
        return lessons.stream().filter(l -> !done.contains(l.id())).findFirst().map(LearnerService::ref).orElse(null);
    }

    private static LessonRef ref(LessonOutline l) {
        return new LessonRef(l.id(), l.title());
    }

    private static String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CROCKFORD[RANDOM.nextInt(CROCKFORD.length)]);
        }
        return sb.toString();
    }
}
