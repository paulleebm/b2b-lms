package com.lms;

import static com.lms.support.Api.as;
import static com.lms.support.Api.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.lms.shared.TenantContext;
import com.lms.support.Api;
import com.lms.support.IntegrationTest;
import com.lms.tenancy.internal.KeycloakAdminClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Scenarios 5–7 end to end: assign → learn (text, video heartbeats) → quiz → completion with a
 * certificate, and the xAPI history it leaves behind.
 */
class LearningFlowIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @MockitoBean
    KeycloakAdminClient keycloak;

    Api api;
    String acme;
    String alice;     // ORG_ADMIN
    String ian;       // INSTRUCTOR
    String dev;       // department "개발팀"
    String courseId;
    String textLesson;
    String videoLesson;

    @BeforeEach
    void setUp() throws Exception {
        api = new Api(mvc, keycloak);
        alice = "alice-" + api.run;
        acme = api.company("acme", alice);
        ian = api.joined(alice, acme, "ian", "INSTRUCTOR");
        dev = read(api.json(post("/v1/departments").with(as(alice, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"개발팀\"}")), "$.id");
        publishedCourse(true);
    }

    @Test
    @DisplayName("부서 배정은 그 부서의 활성 멤버에게 수강을 만들고, 겹치는 개인 배정은 수강 하나 + 더 이른 마감일")
    void departmentAndMemberAssignmentsOverlap() throws Exception {
        String dana = joinedIn("dana");
        String evan = joinedIn("evan");
        String outsider = api.joined(alice, acme, "olga", "LEARNER");

        String first = assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"],\"dueAt\":\"2026-12-31T14:59:59Z\"}", null);
        assertThat((Integer) read(first, "$.targeted")).isEqualTo(2);
        assertThat((Integer) read(first, "$.created")).isEqualTo(2);

        String danaId = membershipId(dana);
        String second = assign("{\"courseId\":\"" + courseId + "\",\"membershipIds\":[\"" + danaId + "\"],\"dueAt\":\"2026-11-30T14:59:59Z\"}", null);
        assertThat((Integer) read(second, "$.created")).isZero();
        assertThat((Integer) read(second, "$.existing")).isEqualTo(1);

        assertThat((String) read(myEnrollments(dana), "$[0].dueAt")).isEqualTo("2026-11-30T14:59:59Z");
        assertThat((String) read(myEnrollments(evan), "$[0].dueAt")).isEqualTo("2026-12-31T14:59:59Z");
        assertThat((List<?>) read(myEnrollments(outsider), "$")).isEmpty();

        // A later assignment cannot push a deadline back.
        assign("{\"courseId\":\"" + courseId + "\",\"membershipIds\":[\"" + danaId + "\"],\"dueAt\":\"2027-06-30T14:59:59Z\"}", null);
        assertThat((String) read(myEnrollments(dana), "$[0].dueAt")).isEqualTo("2026-11-30T14:59:59Z");
    }

    @Test
    @DisplayName("같은 Idempotency-Key로 다시 보내면 처음 결과를 그대로(배정은 한 번만), 다른 내용이면 422")
    void idempotencyKey() throws Exception {
        joinedIn("fay");
        String body = "{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}";
        String key = "assign-" + api.run;

        MockHttpServletResponse first = api.raw(assignRequest(body, key));
        MockHttpServletResponse again = api.raw(assignRequest(body, key));
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(again.getStatus()).isEqualTo(200);
        assertThat(again.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(again.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat((List<?>) read(api.json(get("/v1/assignments").with(as(alice, acme))), "$")).hasSize(1);

        MockHttpServletResponse misuse = api.raw(assignRequest(
                "{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"],\"dueAt\":\"2026-12-01T00:00:00Z\"}", key));
        assertThat(misuse.getStatus()).isEqualTo(422);
        assertThat(misuse.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("신규 입사자: 배정 뒤에 그 부서로 초대받아 가입하면 자동으로 수강이 생긴다. 부서 이동도 마찬가지")
    void newHiresAndDepartmentMoves() throws Exception {
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}", null);

        String email = "gina-" + api.run + "@" + acme + ".test";
        api.json(post("/v1/invitations").with(as(alice, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"" + email + "\"],\"role\":\"LEARNER\",\"departmentId\":\"" + dev + "\"}"));
        String gina = Api.subFor(email);
        api.json(get("/v1/me").with(as(gina, acme)));   // first sign-in → ACTIVE → MemberJoined
        assertThat((String) read(myEnrollments(gina), "$[0].courseId")).isEqualTo(courseId);

        String hank = api.joined(alice, acme, "hank", "LEARNER");
        assertThat((List<?>) read(myEnrollments(hank), "$")).isEmpty();
        api.json(patch("/v1/members/{id}", membershipId(hank)).with(as(alice, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"departmentId\":\"" + dev + "\"}"));
        assertThat((String) read(myEnrollments(hank), "$[0].courseId")).isEqualTo(courseId);
    }

    @Test
    @DisplayName("학습 전체: 텍스트 완료 → 영상 하트비트(건너뛰기는 최대 35초만 인정) → 퀴즈 불합격 → 재응시 합격 → 수료 + 수료증")
    void learnToCompletion() throws Exception {
        String ivy = joinedIn("ivy");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}", null);
        String enrollment = read(myEnrollments(ivy), "$[0].id");

        // The quiz is locked until every lesson is done.
        assertThat(api.raw(post("/v1/my/enrollments/{id}/quiz/attempts", enrollment).with(as(ivy, acme))).getStatus())
                .isEqualTo(409);

        // Text lesson: open, then mark done.
        String text = api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, textLesson).with(as(ivy, acme)));
        assertThat((String) read(text, "$.body")).contains("비밀번호");
        assertThat((String) read(myEnrollments(ivy), "$[0].status")).isEqualTo("IN_PROGRESS");
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/complete", enrollment, textLesson).with(as(ivy, acme)));

        // Video lesson (server duration: 20 s). Jumping to the end earns at most elapsed×2+5 = 5 s.
        String video = api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, videoLesson).with(as(ivy, acme)));
        assertThat((String) read(video, "$.video.format")).isEqualTo("mp4");
        assertThat((Integer) read(video, "$.video.durationSeconds")).isEqualTo(20);
        String skip = heartbeat(ivy, enrollment, 20);
        assertThat((Integer) read(skip, "$.watchedSec")).isLessThanOrEqualTo(5);
        assertThat((Boolean) read(skip, "$.completed")).isFalse();

        // Watching on: rewind and play forward in small steps until 90 % is credited.
        heartbeat(ivy, enrollment, 0);
        String last = null;
        for (int pos = 5; pos <= 20; pos += 5) {
            last = heartbeat(ivy, enrollment, pos);
        }
        assertThat((Boolean) read(last, "$.completed")).isTrue();
        assertThat((Boolean) read(last, "$.courseCompleted")).isFalse();   // a quiz is still ahead

        // Quiz: a wrong answer fails, the same attempt cannot be submitted twice.
        String attempt = api.json(post("/v1/my/enrollments/{id}/quiz/attempts", enrollment).with(as(ivy, acme)));
        assertThat(attempt).doesNotContain("\"answer\"");   // the key never leaves the server
        String attemptId = read(attempt, "$.id");
        String questionId = read(attempt, "$.questions[0].id");
        String failed = submit(ivy, enrollment, attemptId, questionId, "a");
        assertThat((Boolean) read(failed, "$.passed")).isFalse();
        assertThat((Integer) read(failed, "$.attemptsLeft")).isEqualTo(1);
        assertThat(api.raw(submitRequest(ivy, enrollment, attemptId, questionId, "b")).getStatus()).isEqualTo(409);

        // Second attempt passes → the course completes with a certificate, in the same transaction.
        String retry = api.json(post("/v1/my/enrollments/{id}/quiz/attempts", enrollment).with(as(ivy, acme)));
        String passed = submit(ivy, enrollment, (String) read(retry, "$.id"), questionId, "b");
        assertThat((Boolean) read(passed, "$.passed")).isTrue();
        assertThat((Boolean) read(passed, "$.courseCompleted")).isTrue();
        assertThat((String) read(passed, "$.certificateSerial")).matches("LMS-\\d{4}-[0-9A-HJKMNP-TV-Z]{8}");

        String mine = myEnrollments(ivy);
        assertThat((String) read(mine, "$[0].status")).isEqualTo("COMPLETED");
        assertThat((String) read(mine, "$[0].certificateSerial")).isEqualTo(read(passed, "$.certificateSerial"));
        // No attempts after completion.
        assertThat(api.raw(post("/v1/my/enrollments/{id}/quiz/attempts", enrollment).with(as(ivy, acme))).getStatus())
                .isEqualTo(409);

        // The xAPI history, in order, readable like an LRS.
        String statements = api.json(get("/v1/xapi/statements").param("registration", enrollment).with(as(alice, acme)));
        List<String> verbs = read(statements, "$.statements[*].verb.id");
        assertThat(verbs.stream().map(v -> v.substring(v.lastIndexOf('/') + 1)).toList())
                .containsSubsequence("launched", "completed", "launched", "progressed", "completed",
                        "attempted", "failed", "attempted", "passed", "completed");
        assertThat((String) read(statements, "$.statements[0].context.registration")).isEqualTo(enrollment);
        assertThat(statements).contains("https://w3id.org/xapi/cmi5/result/extensions/progress");
    }

    @Test
    @DisplayName("퀴즈가 없는 코스는 마지막 레슨 완료로 수료")
    void courseWithoutQuizCompletesOnLastLesson() throws Exception {
        publishedCourse(false);
        String jay = joinedIn("jay");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}", null);
        String enrollment = read(myEnrollments(jay), "$[?(@.courseId == '" + courseId + "')].id").toString()
                .replaceAll("[\\[\\]\"]", "");

        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, textLesson).with(as(jay, acme)));
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/complete", enrollment, textLesson).with(as(jay, acme)));
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, videoLesson).with(as(jay, acme)));
        String last = null;
        for (int pos = 5; pos <= 20; pos += 5) {
            last = heartbeat(jay, enrollment, pos);
        }
        assertThat((Boolean) read(last, "$.courseCompleted")).isTrue();
        assertThat((String) read(last, "$.certificateSerial")).startsWith("LMS-");
    }

    @Test
    @DisplayName("남의 수강, 다른 회사의 수강은 404. 수강자가 있는 코스는 게시 취소 불가(409)")
    void isolationAndCourseLock() throws Exception {
        String kim = joinedIn("kim");
        String lee = joinedIn("lee");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}", null);
        String kimsEnrollment = read(myEnrollments(kim), "$[0].id");

        assertThat(api.raw(get("/v1/my/enrollments/{id}", kimsEnrollment).with(as(lee, acme))).getStatus()).isEqualTo(404);
        String bob = "bob-" + api.run;
        String beta = api.company("beta", bob);
        assertThat(api.raw(get("/v1/my/enrollments/{id}", kimsEnrollment).with(as(bob, beta))).getStatus()).isEqualTo(404);
        assertThat(api.raw(assignRequestAs(bob, beta, "{\"courseId\":\"" + courseId + "\",\"membershipIds\":[]}")).getStatus())
                .isEqualTo(422);   // acme's course does not exist for beta → not assignable

        MockHttpServletResponse course = api.raw(get("/v1/courses/{id}", courseId).with(as(ian, acme)));
        MockHttpServletResponse unpublish = api.raw(post("/v1/courses/{id}/unpublish", courseId).with(as(ian, acme))
                .header("If-Match", course.getHeader("ETag")));
        assertThat(unpublish.getStatus()).isEqualTo(409);
        assertThat(unpublish.getContentAsString()).contains("COURSE_IN_USE");
    }

    @Test
    @DisplayName("학습 기록(xAPI)은 앱 계정으로 수정·삭제할 수 없다 (추가만 가능)")
    void learningRecordIsAppendOnly() throws Exception {
        String max = joinedIn("max");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}", null);
        String enrollment = read(myEnrollments(max), "$[0].id");
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, textLesson).with(as(max, acme)));

        UUID tenantId = jdbc.sql("select id from tenant where slug = ?").param(acme).query(UUID.class).single();
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s ->
                jdbc.sql("update learning_record set verb_iri = 'x'").update())))
                .rootCause().hasMessageContaining("permission denied");
        assertThatThrownBy(() -> TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s ->
                jdbc.sql("delete from learning_record").update())))
                .rootCause().hasMessageContaining("permission denied");
    }

    // --- helpers ------------------------------------------------------------------------------

    /** A published course: text lesson + 20-second video lesson (+ one-question quiz, 2 attempts). */
    private void publishedCourse(boolean withQuiz) throws Exception {
        MockHttpServletResponse created = api.raw(post("/v1/courses").with(as(ian, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"보안 기초\"}"));
        String course = created.getContentAsString(StandardCharsets.UTF_8);
        String etag = created.getHeader("ETag");
        courseId = read(course, "$.id");

        var r = write(post("/v1/courses/{id}/sections", courseId), etag, "{\"title\":\"1장\"}");
        String section = read(r.body, "$.sections[0].id");
        r = write(post("/v1/courses/{id}/sections/{s}/lessons", courseId, section), r.etag, "{\"title\":\"규칙\",\"type\":\"TEXT\"}");
        textLesson = read(r.body, "$.sections[0].lessons[0].id");
        r = write(patch("/v1/courses/{id}/lessons/{l}", courseId, textLesson), r.etag, "{\"body\":\"비밀번호는 12자 이상\"}");
        r = write(post("/v1/courses/{id}/sections/{s}/lessons", courseId, section), r.etag, "{\"title\":\"영상\",\"type\":\"VIDEO\"}");
        videoLesson = read(r.body, "$.sections[0].lessons[1].id");
        r = write(patch("/v1/courses/{id}/lessons/{l}", courseId, videoLesson), r.etag, "{\"videoId\":\"" + readyVideo() + "\"}");
        if (withQuiz) {
            r = write(put("/v1/courses/{id}/quiz", courseId), r.etag, """
                    {"passScore": 100, "maxAttempts": 2, "questions": [
                      {"type": "SINGLE", "prompt": "최소 길이는?", "options": ["8자", "12자", "4자"], "correct": [1], "points": 10}
                    ]}""");
            assertThat((Integer) read(r.body, "$.quiz.revision")).isEqualTo(1);
            assertThat((List<String>) read(r.body, "$.quiz.questions[0].answer")).containsExactly("b");
        }
        // The fake transcoder needs a moment before the video is READY (publish requires it).
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse publish = api.raw(post("/v1/courses/{id}/publish", courseId).with(as(ian, acme))
                    .header("If-Match", r.etag));
            if (publish.getStatus() == 200) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("course never became publishable");
    }

    private String readyVideo() throws Exception {
        byte[] bytes = new byte[1024];
        String init = api.json(post("/v1/uploads").with(as(ian, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"VIDEO_SOURCE\",\"fileName\":\"v.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":" + bytes.length + "}"));
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(read(init, "$.url")))
                        .header("Content-Type", "video/mp4").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.discarding());
        return read(api.json(post("/v1/uploads/{id}/complete", (String) read(init, "$.fileId")).with(as(ian, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"durationSeconds\":20}")), "$.videoId");
    }

    record Written(String body, String etag) {
    }

    private Written write(MockHttpServletRequestBuilder request, String etag, String json) throws Exception {
        MockHttpServletResponse r = api.raw(request.with(as(ian, acme)).header("If-Match", etag)
                .contentType(MediaType.APPLICATION_JSON).content(json));
        String body = r.getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getStatus()).as(body).isEqualTo(200);
        return new Written(body, r.getHeader("ETag"));
    }

    /** A learner who has joined and sits in the 개발팀 department. */
    private String joinedIn(String name) throws Exception {
        String sub = api.joined(alice, acme, name, "LEARNER");
        api.json(patch("/v1/members/{id}", membershipId(sub)).with(as(alice, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"departmentId\":\"" + dev + "\"}"));
        return sub;
    }

    private String membershipId(String sub) throws Exception {
        List<String> ids = read(api.json(get("/v1/members").with(as(alice, acme))),
                "$[?(@.email == '" + sub + "@example.com')].id");
        return ids.getFirst();
    }

    private String assign(String body, String key) throws Exception {
        return api.json(assignRequest(body, key));
    }

    private MockHttpServletRequestBuilder assignRequest(String body, String key) {
        MockHttpServletRequestBuilder r = post("/v1/assignments").with(as(alice, acme))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return key == null ? r : r.header("Idempotency-Key", key);
    }

    private MockHttpServletRequestBuilder assignRequestAs(String sub, String org, String body) {
        return post("/v1/assignments").with(as(sub, org)).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String myEnrollments(String sub) throws Exception {
        return api.json(get("/v1/my/enrollments").with(as(sub, acme)));
    }

    private String heartbeat(String sub, String enrollment, int position) throws Exception {
        return api.json(put("/v1/my/enrollments/{e}/lessons/{l}/progress", enrollment, videoLesson).with(as(sub, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"positionSec\":" + position + "}"));
    }

    private String submit(String sub, String enrollment, String attemptId, String questionId, String option) throws Exception {
        return api.json(submitRequest(sub, enrollment, attemptId, questionId, option));
    }

    private MockHttpServletRequestBuilder submitRequest(String sub, String enrollment, String attemptId,
                                                        String questionId, String option) {
        return post("/v1/my/enrollments/{e}/quiz/attempts/{a}/submit", enrollment, attemptId).with(as(sub, acme))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\":{\"" + questionId + "\":[\"" + option + "\"]}}");
    }
}
