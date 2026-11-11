package com.lms.catalog.internal;

import com.lms.catalog.internal.CatalogService.CourseDetail;
import com.lms.shared.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Course studio API. Concurrency follows HTTP itself (RFC 9110): every response carries
 * {@code ETag: "<version>"}, every write must send it back as {@code If-Match}.
 * Missing → 428, stale → 412. Two instructors editing one course cannot silently overwrite
 * each other.
 */
@RestController
@RequestMapping("/v1/courses")
@PreAuthorize("hasAnyRole('ORG_ADMIN','INSTRUCTOR')")
class CourseController {

    private final CatalogService catalog;

    CourseController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    List<CatalogService.CourseSummary> list() {
        return catalog.list();
    }

    @PostMapping
    ResponseEntity<CourseDetail> create(@Valid @RequestBody CreateCourseRequest request) {
        CourseDetail created = catalog.create(request.title().strip(), request.description());
        return ResponseEntity.created(URI.create("/v1/courses/" + created.id()))
                .eTag(etag(created)).body(created);
    }

    @GetMapping("/{id}")
    ResponseEntity<CourseDetail> get(@PathVariable UUID id) {
        return withEtag(catalog.get(id));
    }

    @PatchMapping("/{id}")
    ResponseEntity<CourseDetail> update(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                        @Valid @RequestBody UpdateCourseRequest request) {
        return withEtag(catalog.update(id, version(ifMatch), strip(request.title()), request.description()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        catalog.delete(id, version(ifMatch));
        return ResponseEntity.noContent().build();
    }

    /** 422 COURSE_NOT_READY with {@code errors: [{code, message, sectionId, lessonId}]} when incomplete. */
    @PostMapping("/{id}/publish")
    ResponseEntity<CourseDetail> publish(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withEtag(catalog.publish(id, version(ifMatch)));
    }

    @PostMapping("/{id}/unpublish")
    ResponseEntity<CourseDetail> unpublish(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withEtag(catalog.unpublish(id, version(ifMatch)));
    }

    @PostMapping("/{id}/archive")
    ResponseEntity<CourseDetail> archive(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withEtag(catalog.archive(id, version(ifMatch)));
    }

    @PostMapping("/{id}/sections")
    ResponseEntity<CourseDetail> addSection(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                            @Valid @RequestBody TitleRequest request) {
        return withEtag(catalog.addSection(id, version(ifMatch), request.title().strip()));
    }

    @PatchMapping("/{id}/sections/{sectionId}")
    ResponseEntity<CourseDetail> renameSection(@PathVariable UUID id, @PathVariable UUID sectionId,
                                               @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                               @Valid @RequestBody TitleRequest request) {
        return withEtag(catalog.renameSection(id, sectionId, version(ifMatch), request.title().strip()));
    }

    @DeleteMapping("/{id}/sections/{sectionId}")
    ResponseEntity<CourseDetail> deleteSection(@PathVariable UUID id, @PathVariable UUID sectionId,
                                               @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withEtag(catalog.deleteSection(id, sectionId, version(ifMatch)));
    }

    @PostMapping("/{id}/sections/{sectionId}/lessons")
    ResponseEntity<CourseDetail> addLesson(@PathVariable UUID id, @PathVariable UUID sectionId,
                                           @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                           @Valid @RequestBody AddLessonRequest request) {
        return withEtag(catalog.addLesson(id, sectionId, version(ifMatch), request.title().strip(), request.type()));
    }

    /** Partial: rename, write the text body, or attach an uploaded video / document. */
    @PatchMapping("/{id}/lessons/{lessonId}")
    ResponseEntity<CourseDetail> updateLesson(@PathVariable UUID id, @PathVariable UUID lessonId,
                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                              @Valid @RequestBody UpdateLessonRequest request) {
        return withEtag(catalog.updateLesson(id, lessonId, version(ifMatch), new CatalogService.LessonChange(
                strip(request.title()), request.body(), request.videoId(), request.documentId())));
    }

    @DeleteMapping("/{id}/lessons/{lessonId}")
    ResponseEntity<CourseDetail> deleteLesson(@PathVariable UUID id, @PathVariable UUID lessonId,
                                              @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withEtag(catalog.deleteLesson(id, lessonId, version(ifMatch)));
    }

    /** Full new order: sections, and the lessons in each (lessons may move between sections). */
    @PutMapping("/{id}/outline")
    ResponseEntity<CourseDetail> reorder(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                         @Valid @RequestBody OutlineRequest request) {
        return withEtag(catalog.reorder(id, version(ifMatch), request.sections().stream()
                .map(s -> new CatalogService.OutlineSection(s.id(), s.lessonIds())).toList()));
    }

    /** Replaces the whole quiz (settings and every question). Draft courses only. */
    @PutMapping("/{id}/quiz")
    ResponseEntity<CourseDetail> saveQuiz(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
                                          @Valid @RequestBody QuizRequest request) {
        return withEtag(catalog.saveQuiz(id, version(ifMatch), new CatalogService.QuizInput(
                request.passScore(), request.maxAttempts(), request.timeLimitSec(),
                request.questions().stream().map(q -> new CatalogService.QuestionInput(
                        q.type(), q.prompt(), q.options(), q.correct(), q.points())).toList())));
    }

    @DeleteMapping("/{id}/quiz")
    ResponseEntity<CourseDetail> deleteQuiz(@PathVariable UUID id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        return withEtag(catalog.deleteQuiz(id, version(ifMatch)));
    }

    // --- ETag / If-Match ------------------------------------------------------------------------

    private static ResponseEntity<CourseDetail> withEtag(CourseDetail detail) {
        return ResponseEntity.ok().eTag(etag(detail)).body(detail);
    }

    private static String etag(CourseDetail detail) {
        return "\"" + detail.version() + "\"";
    }

    /** Accepts {@code "3"} (and the weak form {@code W/"3"} some proxies produce). */
    static long version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiException.preconditionRequired("IF_MATCH_REQUIRED",
                    "수정하려면 If-Match 헤더에 마지막으로 받은 ETag를 보내야 합니다.");
        }
        String value = ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiException.preconditionFailed("COURSE_CHANGED", "알 수 없는 ETag입니다. 코스를 다시 불러오세요.");
        }
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }

    // --- requests -------------------------------------------------------------------------------

    record CreateCourseRequest(@NotBlank @Size(max = 200) String title, @Size(max = 5000) String description) {
    }

    record UpdateCourseRequest(@Size(min = 1, max = 200) String title, @Size(max = 5000) String description) {
    }

    record TitleRequest(@NotBlank @Size(max = 200) String title) {
    }

    record AddLessonRequest(@NotBlank @Size(max = 200) String title, @NotNull Lesson.Type type) {
    }

    record UpdateLessonRequest(@Size(min = 1, max = 200) String title, @Size(max = 100_000) String body,
                               UUID videoId, UUID documentId) {
    }

    record OutlineRequest(@NotNull @Size(max = 200) List<@Valid OutlineEntry> sections) {
    }

    record OutlineEntry(@NotNull UUID id, @NotNull @Size(max = 500) List<@NotNull UUID> lessonIds) {
    }

    /**
     * @param passScore    percent of points needed to pass
     * @param maxAttempts  null = unlimited
     * @param timeLimitSec null = no limit
     */
    record QuizRequest(
            @Min(1) @Max(100) int passScore,
            @Min(1) @Max(20) Integer maxAttempts,
            @Min(60) @Max(14_400) Integer timeLimitSec,
            @NotEmpty @Size(max = 50) List<@Valid QuestionRequest> questions) {
    }

    /** @param correct indexes into {@code options} (0-based) */
    record QuestionRequest(
            @NotNull Question.Type type,
            @NotBlank @Size(max = 2000) String prompt,
            @NotNull @Size(max = 8) List<@Size(max = 500) String> options,
            @NotNull @Size(max = 8) List<@NotNull Integer> correct,
            @Min(1) @Max(100) int points) {
    }
}
