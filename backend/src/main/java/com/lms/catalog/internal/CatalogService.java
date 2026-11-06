package com.lms.catalog.internal;

import com.lms.shared.web.ApiNullable;
import com.lms.files.MediaLibrary;
import com.lms.files.MediaLibrary.DocumentSummary;
import com.lms.files.MediaLibrary.VideoState;
import com.lms.files.MediaLibrary.VideoSummary;
import com.lms.shared.TenantContext;
import com.lms.shared.web.ApiException;
import com.lms.tenancy.MemberDirectory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course authoring for instructors and admins. Every write:
 * <ol>
 *   <li>loads the course and checks the caller may edit it (owner, or company admin),</li>
 *   <li>checks the client's If-Match version (412 when stale),</li>
 *   <li>changes the outline and {@code touch()}es the course, so its version moves,</li>
 *   <li>returns the whole course again with the new version (the client's next If-Match).</li>
 * </ol>
 */
@Service
class CatalogService {

    private final CourseRepository courses;
    private final CourseSectionRepository sections;
    private final LessonRepository lessons;
    private final MediaLibrary media;
    private final MemberDirectory members;

    CatalogService(CourseRepository courses, CourseSectionRepository sections, LessonRepository lessons,
                   MediaLibrary media, MemberDirectory members) {
        this.courses = courses;
        this.sections = sections;
        this.lessons = lessons;
        this.media = media;
        this.members = members;
    }

    // --- views --------------------------------------------------------------------------------

    /** READY = usable by learners; MISSING = nothing attached / no text yet. */
    enum ContentState { READY, PROCESSING, MISSING, FAILED }

    record CourseSummary(UUID id, String title, Course.Status status, UUID ownerId, @ApiNullable String ownerName,
                         long sectionCount, long lessonCount, long version, Instant updatedAt) {
    }

    record CourseDetail(UUID id, String title, String description, Course.Status status, UUID ownerId,
                        @ApiNullable String ownerName, long version,
                        @ApiNullable Instant publishedAt, Instant updatedAt,
                        List<SectionView> sections) {
    }

    record SectionView(UUID id, String title, List<LessonView> lessons) {
    }

    record LessonView(UUID id, String title, Lesson.Type type, ContentState contentState, @ApiNullable String body,
                      @ApiNullable VideoSummary video, @ApiNullable DocumentSummary document) {
    }

    record PublishProblem(String code, String message, UUID sectionId, UUID lessonId) {
    }

    // --- queries ------------------------------------------------------------------------------

    /** Admins see every course in the company; instructors see their own. */
    @Transactional(readOnly = true)
    List<CourseSummary> list() {
        List<Course> found = isAdmin()
                ? courses.findAllByOrderByUpdatedAtDesc()
                : courses.findAllByOwnerIdOrderByUpdatedAtDesc(TenantContext.requireMembershipId());
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(Course::getId).toList();
        Map<UUID, CourseRepository.OutlineCount> counts = courses.countOutlines(ids).stream()
                .collect(Collectors.toMap(CourseRepository.OutlineCount::getCourseId, Function.identity()));
        Map<UUID, String> names = members.displayNames(found.stream().map(Course::getOwnerId).collect(Collectors.toSet()));
        return found.stream().map(c -> {
            var count = counts.get(c.getId());
            return new CourseSummary(c.getId(), c.getTitle(), c.getStatus(), c.getOwnerId(), names.get(c.getOwnerId()),
                    count == null ? 0 : count.getSections(), count == null ? 0 : count.getLessons(),
                    c.getVersion(), c.getUpdatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    CourseDetail get(UUID courseId) {
        return detail(readable(courseId));
    }

    // --- course -------------------------------------------------------------------------------

    @Transactional
    CourseDetail create(String title, String description) {
        Course course = courses.saveAndFlush(new Course(title, description, TenantContext.requireMembershipId()));
        return detail(course);
    }

    @Transactional
    CourseDetail update(UUID courseId, long expectedVersion, String title, String description) {
        Course course = editable(courseId, expectedVersion);
        course.rename(title, description);
        return saved(course);
    }

    /** Only drafts are deleted; a published course is archived so its history stays. */
    @Transactional
    void delete(UUID courseId, long expectedVersion) {
        Course course = editable(courseId, expectedVersion);
        if (course.getStatus() != Course.Status.DRAFT) {
            throw ApiException.conflict("COURSE_NOT_DRAFT", "초안만 삭제할 수 있습니다. 게시된 코스는 보관하세요.");
        }
        courses.delete(course);   // sections and lessons go with it (ON DELETE CASCADE)
    }

    @Transactional
    CourseDetail publish(UUID courseId, long expectedVersion) {
        Course course = editable(courseId, expectedVersion);
        course.requireOutlineEditable();
        List<PublishProblem> problems = publishProblems(course);
        if (!problems.isEmpty()) {
            throw ApiException.unprocessable("COURSE_NOT_READY", "게시하기 전에 고칠 곳이 " + problems.size() + "군데 있습니다.")
                    .withErrors(problems);
        }
        course.publish();
        return saved(course);
    }

    @Transactional
    CourseDetail unpublish(UUID courseId, long expectedVersion) {
        Course course = editable(courseId, expectedVersion);
        course.unpublish();
        return saved(course);
    }

    @Transactional
    CourseDetail archive(UUID courseId, long expectedVersion) {
        Course course = editable(courseId, expectedVersion);
        course.archive();
        return saved(course);
    }

    // --- sections -----------------------------------------------------------------------------

    @Transactional
    CourseDetail addSection(UUID courseId, long expectedVersion, String title) {
        Course course = outlineEditable(courseId, expectedVersion);
        int position = sections.findAllByCourseIdOrderByPosition(courseId).size();
        sections.save(new CourseSection(courseId, title, position));
        course.touch();
        return saved(course);
    }

    @Transactional
    CourseDetail renameSection(UUID courseId, UUID sectionId, long expectedVersion, String title) {
        Course course = outlineEditable(courseId, expectedVersion);
        section(courseId, sectionId).rename(title);
        course.touch();
        return saved(course);
    }

    @Transactional
    CourseDetail deleteSection(UUID courseId, UUID sectionId, long expectedVersion) {
        Course course = outlineEditable(courseId, expectedVersion);
        sections.delete(section(courseId, sectionId));
        renumberSections(courseId, sectionId);
        course.touch();
        return saved(course);
    }

    // --- lessons ------------------------------------------------------------------------------

    @Transactional
    CourseDetail addLesson(UUID courseId, UUID sectionId, long expectedVersion, String title, Lesson.Type type) {
        Course course = outlineEditable(courseId, expectedVersion);
        section(courseId, sectionId);
        int position = lessons.findAllBySectionIdOrderByPosition(sectionId).size();
        lessons.save(new Lesson(courseId, sectionId, title, type, position));
        course.touch();
        return saved(course);
    }

    record LessonChange(String title, String body, UUID videoId, UUID documentId) {
    }

    @Transactional
    CourseDetail updateLesson(UUID courseId, UUID lessonId, long expectedVersion, LessonChange change) {
        Course course = outlineEditable(courseId, expectedVersion);
        Lesson lesson = lesson(courseId, lessonId);
        if (change.title() != null) {
            lesson.rename(change.title());
        }
        if (change.body() != null) {
            requireType(lesson, Lesson.Type.TEXT, "본문은 텍스트 레슨에만 쓸 수 있습니다.");
            lesson.writeBody(change.body());
        }
        if (change.videoId() != null) {
            requireType(lesson, Lesson.Type.VIDEO, "영상은 영상 레슨에만 붙일 수 있습니다.");
            // Tenant-scoped lookup: another company's video id is simply "not found".
            if (!media.videos(List.of(change.videoId())).containsKey(change.videoId())) {
                throw ApiException.notFound("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다.");
            }
            lesson.attachVideo(change.videoId());
        }
        if (change.documentId() != null) {
            requireType(lesson, Lesson.Type.DOCUMENT, "문서는 문서 레슨에만 붙일 수 있습니다.");
            DocumentSummary doc = media.documents(List.of(change.documentId())).get(change.documentId());
            if (doc == null) {
                throw ApiException.notFound("FILE_NOT_FOUND", "파일을 찾을 수 없습니다.");
            }
            if (!doc.available()) {
                throw ApiException.unprocessable("FILE_NOT_UPLOADED", "업로드가 끝나지 않은 파일입니다.");
            }
            lesson.attachDocument(change.documentId());
        }
        course.touch();
        return saved(course);
    }

    @Transactional
    CourseDetail deleteLesson(UUID courseId, UUID lessonId, long expectedVersion) {
        Course course = outlineEditable(courseId, expectedVersion);
        Lesson lesson = lesson(courseId, lessonId);
        lessons.delete(lesson);
        renumberLessons(lesson.getSectionId(), lessonId);
        course.touch();
        return saved(course);
    }

    // --- outline (reorder / move) -------------------------------------------------------------

    record OutlineSection(UUID id, List<UUID> lessonIds) {
    }

    /**
     * Replaces the order of everything in one request: section order, and which lessons sit in
     * which section in what order. The request must mention exactly the existing sections and
     * lessons — it reorders, it never creates or deletes.
     */
    @Transactional
    CourseDetail reorder(UUID courseId, long expectedVersion, List<OutlineSection> outline) {
        Course course = outlineEditable(courseId, expectedVersion);
        Map<UUID, CourseSection> existingSections = sections.findAllByCourseIdOrderByPosition(courseId).stream()
                .collect(Collectors.toMap(CourseSection::getId, Function.identity()));
        Map<UUID, Lesson> existingLessons = lessons.findAllByCourseIdOrderByPosition(courseId).stream()
                .collect(Collectors.toMap(Lesson::getId, Function.identity()));

        List<UUID> requestedSections = outline.stream().map(OutlineSection::id).toList();
        List<UUID> requestedLessons = outline.stream().flatMap(s -> s.lessonIds().stream()).toList();
        if (!sameMembers(requestedSections, existingSections.keySet())
                || !sameMembers(requestedLessons, existingLessons.keySet())) {
            throw ApiException.conflict("OUTLINE_MISMATCH",
                    "목차가 그사이 바뀌었습니다. 최신 목차를 불러온 뒤 다시 정렬하세요.");
        }

        // The unique (parent, position) constraints are DEFERRABLE: rows may swap positions
        // freely here and are checked once, at commit.
        for (int s = 0; s < outline.size(); s++) {
            OutlineSection entry = outline.get(s);
            existingSections.get(entry.id()).moveTo(s);
            for (int l = 0; l < entry.lessonIds().size(); l++) {
                existingLessons.get(entry.lessonIds().get(l)).moveTo(entry.id(), l);
            }
        }
        course.touch();
        return saved(course);
    }

    // --- publish rules ------------------------------------------------------------------------

    private List<PublishProblem> publishProblems(Course course) {
        CourseDetail d = detail(course);
        List<PublishProblem> problems = new ArrayList<>();
        if (d.sections().isEmpty()) {
            problems.add(new PublishProblem("NO_SECTIONS", "섹션을 하나 이상 만드세요.", null, null));
        }
        for (SectionView s : d.sections()) {
            if (s.lessons().isEmpty()) {
                problems.add(new PublishProblem("EMPTY_SECTION", "‘" + s.title() + "’ 섹션에 레슨이 없습니다.", s.id(), null));
            }
            for (LessonView l : s.lessons()) {
                String problem = switch (l.contentState()) {
                    case READY -> null;
                    case PROCESSING -> "영상 변환이 아직 끝나지 않았습니다.";
                    case FAILED -> "영상 변환에 실패했습니다. 영상을 다시 올리세요.";
                    case MISSING -> switch (l.type()) {
                        case VIDEO -> "영상을 올리세요.";
                        case DOCUMENT -> "문서를 올리세요.";
                        case TEXT -> "본문을 쓰세요.";
                    };
                };
                if (problem != null) {
                    problems.add(new PublishProblem("LESSON_" + l.contentState(),
                            "‘" + l.title() + "’: " + problem, s.id(), l.id()));
                }
            }
        }
        return problems;
    }

    // --- helpers ------------------------------------------------------------------------------

    private CourseDetail saved(Course course) {
        courses.flush();   // run the versioned UPDATE now, so the response carries the new version
        return detail(course);
    }

    private CourseDetail detail(Course course) {
        List<CourseSection> sectionRows = sections.findAllByCourseIdOrderByPosition(course.getId());
        List<Lesson> lessonRows = lessons.findAllByCourseIdOrderByPosition(course.getId());

        Map<UUID, VideoSummary> videos = media.videos(lessonRows.stream()
                .map(Lesson::getVideoAssetId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, DocumentSummary> docs = media.documents(lessonRows.stream()
                .map(Lesson::getFileId).filter(Objects::nonNull).collect(Collectors.toSet()));

        Map<UUID, List<LessonView>> bySection = new LinkedHashMap<>();
        for (Lesson l : lessonRows) {
            VideoSummary video = l.getVideoAssetId() == null ? null : videos.get(l.getVideoAssetId());
            DocumentSummary doc = l.getFileId() == null ? null : docs.get(l.getFileId());
            bySection.computeIfAbsent(l.getSectionId(), k -> new ArrayList<>())
                    .add(new LessonView(l.getId(), l.getTitle(), l.getType(), state(l, video, doc),
                            l.getBody(), video, doc));
        }
        List<SectionView> outline = sectionRows.stream()
                .map(s -> new SectionView(s.getId(), s.getTitle(), bySection.getOrDefault(s.getId(), List.of())))
                .toList();
        String ownerName = members.displayNames(List.of(course.getOwnerId())).get(course.getOwnerId());
        return new CourseDetail(course.getId(), course.getTitle(), course.getDescription(), course.getStatus(),
                course.getOwnerId(), ownerName, course.getVersion(), course.getPublishedAt(), course.getUpdatedAt(),
                outline);
    }

    private static ContentState state(Lesson l, VideoSummary video, DocumentSummary doc) {
        return switch (l.getType()) {
            case TEXT -> l.getBody() == null || l.getBody().isBlank() ? ContentState.MISSING : ContentState.READY;
            case DOCUMENT -> doc != null && doc.available() ? ContentState.READY : ContentState.MISSING;
            case VIDEO -> video == null ? ContentState.MISSING : switch (video.state()) {
                case READY -> ContentState.READY;
                case PROCESSING -> ContentState.PROCESSING;
                case FAILED -> ContentState.FAILED;
            };
        };
    }

    private Course readable(UUID courseId) {
        Course course = courses.findById(courseId)
                .orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "코스를 찾을 수 없습니다."));
        requireOwnerOrAdmin(course);
        return course;
    }

    private Course editable(UUID courseId, long expectedVersion) {
        Course course = readable(courseId);
        course.expectVersion(expectedVersion);
        return course;
    }

    private Course outlineEditable(UUID courseId, long expectedVersion) {
        Course course = editable(courseId, expectedVersion);
        course.requireOutlineEditable();
        return course;
    }

    private void requireOwnerOrAdmin(Course course) {
        if (!isAdmin() && !course.getOwnerId().equals(TenantContext.requireMembershipId())) {
            throw ApiException.forbidden("NOT_COURSE_OWNER", "자신이 만든 코스만 수정할 수 있습니다.");
        }
    }

    private CourseSection section(UUID courseId, UUID sectionId) {
        return sections.findById(sectionId).filter(s -> s.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.notFound("SECTION_NOT_FOUND", "섹션을 찾을 수 없습니다."));
    }

    private Lesson lesson(UUID courseId, UUID lessonId) {
        return lessons.findById(lessonId).filter(l -> l.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "레슨을 찾을 수 없습니다."));
    }

    private void renumberSections(UUID courseId, UUID removedId) {
        int position = 0;
        for (CourseSection s : sections.findAllByCourseIdOrderByPosition(courseId)) {
            if (!s.getId().equals(removedId)) {
                s.moveTo(position++);
            }
        }
    }

    private void renumberLessons(UUID sectionId, UUID removedId) {
        int position = 0;
        for (Lesson l : lessons.findAllBySectionIdOrderByPosition(sectionId)) {
            if (!l.getId().equals(removedId)) {
                l.moveTo(sectionId, position++);
            }
        }
    }

    private static void requireType(Lesson lesson, Lesson.Type expected, String message) {
        if (lesson.getType() != expected) {
            throw ApiException.badRequest("WRONG_LESSON_TYPE", message);
        }
    }

    private static boolean sameMembers(List<UUID> requested, Set<UUID> existing) {
        return requested.size() == existing.size() && new HashSet<>(requested).equals(existing);
    }

    private static boolean isAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch("ROLE_ORG_ADMIN"::equals);
    }
}
