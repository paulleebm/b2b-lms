package com.lms.learning.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * "My learning". Any active member may learn (admins and instructors take required courses too);
 * every endpoint only ever sees the caller's own enrollments.
 */
@RestController
class LearnerController {

    private final LearnerService learner;
    private final QuizTakingService quiz;

    LearnerController(LearnerService learner, QuizTakingService quiz) {
        this.learner = learner;
        this.quiz = quiz;
    }

    @GetMapping("/v1/my/enrollments")
    List<LearnerService.MyEnrollment> myEnrollments() {
        return learner.myEnrollments();
    }

    @GetMapping("/v1/my/enrollments/{id}")
    LearnerService.EnrollmentDetail detail(@PathVariable UUID id) {
        return learner.detail(id);
    }

    /** For HLS in AWS the response also sets the CloudFront cookies for this video's folder. */
    @PostMapping("/v1/my/enrollments/{id}/lessons/{lessonId}/launch")
    LearnerService.LessonContent launch(@PathVariable UUID id, @PathVariable UUID lessonId,
                                        jakarta.servlet.http.HttpServletResponse response) {
        LearnerService.LessonContent content = learner.launch(id, lessonId);
        if (content.video() != null) {
            content.video().cookies().forEach(c -> response.addHeader("Set-Cookie", c.toSetCookieHeader()));
        }
        return content;
    }

    @PutMapping("/v1/my/enrollments/{id}/lessons/{lessonId}/progress")
    LearnerService.ProgressResult heartbeat(@PathVariable UUID id, @PathVariable UUID lessonId,
                                            @Valid @RequestBody HeartbeatRequest request) {
        return learner.heartbeat(id, lessonId, request.positionSec());
    }

    @PostMapping("/v1/my/enrollments/{id}/lessons/{lessonId}/complete")
    LearnerService.ProgressResult complete(@PathVariable UUID id, @PathVariable UUID lessonId) {
        return learner.markDone(id, lessonId);
    }

    @PostMapping("/v1/my/enrollments/{id}/quiz/attempts")
    QuizTakingService.AttemptView startQuiz(@PathVariable UUID id) {
        return quiz.start(id);
    }

    /** {@code answers}: question id → the chosen option ids. */
    @PostMapping("/v1/my/enrollments/{id}/quiz/attempts/{attemptId}/submit")
    QuizTakingService.SubmitResult submit(@PathVariable UUID id, @PathVariable UUID attemptId,
                                          @Valid @RequestBody SubmitRequest request) {
        return quiz.submit(id, attemptId, request.answers());
    }

    record HeartbeatRequest(@Min(0) @Max(86_400) int positionSec) {
    }

    record SubmitRequest(@NotNull @Size(max = 50) Map<UUID, @NotNull @Size(max = 8) List<String>> answers) {
    }
}
