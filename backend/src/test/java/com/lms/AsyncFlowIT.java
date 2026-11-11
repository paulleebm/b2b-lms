package com.lms;

import static com.lms.support.Api.as;
import static com.lms.support.Api.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.lms.shared.TenantContext;
import com.lms.support.Api;
import com.lms.support.AsyncContainers;
import com.lms.support.IntegrationTest;
import com.lms.tenancy.internal.KeycloakAdminClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Day 4 end to end: outbox → SQS (ElasticMQ) → worker. Completion renders a real PDF with Korean
 * text, mails arrive in Mailpit, reminders are throttled, the dashboard counts, the audit log
 * records, and MediaConvert completion events update videos.
 */
class AsyncFlowIT extends IntegrationTest {

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
    String alice;
    String ian;
    String dev;
    String courseId;
    String lessonId;

    @BeforeEach
    void setUp() throws Exception {
        api = new Api(mvc, keycloak);
        alice = "alice-" + api.run;
        acme = api.company("acme", alice);
        ian = api.joined(alice, acme, "ian", "INSTRUCTOR");
        dev = read(api.json(post("/v1/departments").with(as(alice, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"개발팀\"}")), "$.id");
        textCourse("개인정보보호 기초");
    }

    @Test
    @DisplayName("수료하면 워커가 큐에서 받아 한글 수료증 PDF를 만들고, 수료 메일을 보낸다. 진위 확인은 로그인 없이 된다")
    void completionProducesCertificateAndMail() throws Exception {
        String lee = learnerIn("lee");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}");
        String enrollment = read(api.json(get("/v1/my/enrollments").with(as(lee, acme))), "$[0].id");
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, lessonId).with(as(lee, acme)));
        String done = api.json(post("/v1/my/enrollments/{e}/lessons/{l}/complete", enrollment, lessonId).with(as(lee, acme)));
        String serial = read(done, "$.certificateSerial");

        // The worker renders asynchronously: 409 until it is there, then a download link.
        String[] link = new String[1];
        await("certificate PDF", () -> {
            try {
                MockHttpServletResponse r = api.raw(get("/v1/my/enrollments/{e}/certificate", enrollment).with(as(lee, acme)));
                if (r.getStatus() == 200) {
                    link[0] = read(r.getContentAsString(StandardCharsets.UTF_8), "$.url");
                    return true;
                }
                assertThat(r.getStatus()).isEqualTo(409);
                return false;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        byte[] pdf = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(link[0])).build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
        try (var document = Loader.loadPDF(pdf)) {
            // Letter-spaced headings come out with extra spaces: compare without whitespace.
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("수료증", "개인정보보호기초", serial, lee);   // Korean glyphs embedded
        }

        await("completion mail", () -> AsyncContainers.mailsTo(lee + "@example.com").contains("[수료]"));

        // Public check: no token at all, masked name.
        MockHttpServletResponse verify = api.raw(get("/v1/public/certificates/{s}", serial));
        assertThat(verify.getStatus()).isEqualTo(200);
        String body = verify.getContentAsString(StandardCharsets.UTF_8);
        assertThat((String) read(body, "$.courseTitle")).isEqualTo("개인정보보호 기초");
        assertThat((String) read(body, "$.holderName")).startsWith(lee.substring(0, 1)).contains("*").isNotEqualTo(lee);
        assertThat(api.raw(get("/v1/public/certificates/{s}", "LMS-2026-ZZZZZZZZ")).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("배정하면 새로 수강하게 된 사람에게 배정 안내 메일이 간다")
    void assignmentMail() throws Exception {
        String max = learnerIn("max");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"],\"dueAt\":\"2026-12-31T14:59:59Z\"}");
        await("assignment mail", () -> AsyncContainers.mailsTo(max + "@example.com").contains("[필수 교육] 개인정보보호 기초"));
    }

    @Test
    @DisplayName("리마인더는 하루 한 번만: 연달아 눌러도 두 번째는 0건. 메일이 도착한다")
    void remindersAreThrottled() throws Exception {
        String kim = learnerIn("kim");
        String kai = learnerIn("kai");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"],\"dueAt\":\"2026-01-01T00:00:00Z\"}");
        String kaisEnrollment = read(api.json(get("/v1/my/enrollments").with(as(kai, acme))), "$[0].id");

        // One row first (the table's button), then "everyone": kai is already reminded today.
        String one = api.json(post("/v1/reminders").with(as(alice, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"enrollmentIds\":[\"" + kaisEnrollment + "\"]}"));
        String first = api.json(post("/v1/reminders").with(as(alice, acme)));
        String second = api.json(post("/v1/reminders").with(as(alice, acme)));
        assertThat((Integer) read(one, "$.queued")).isEqualTo(1);
        assertThat((Integer) read(first, "$.queued")).isEqualTo(1);
        assertThat((Integer) read(second, "$.queued")).isZero();
        await("reminder mail", () -> AsyncContainers.mailsTo(kim + "@example.com").contains("[마감 지남]"));
    }

    @Test
    @DisplayName("대시보드: 이수율·부서별·미이수자. 수료하면 캐시가 비워져 바로 반영된다")
    void dashboard() throws Exception {
        String a = learnerIn("ann");
        String b = learnerIn("bob");
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"],\"dueAt\":\"2026-01-01T00:00:00Z\"}");

        String before = api.json(get("/v1/dashboard").with(as(alice, acme)));
        assertThat((Integer) read(before, "$.kpi.enrolled")).isEqualTo(2);
        assertThat((Integer) read(before, "$.kpi.overdue")).isEqualTo(2);
        assertThat((Integer) read(before, "$.departments[0].enrolled")).isEqualTo(2);
        assertThat((List<String>) read(before, "$.pending[*].memberName")).containsExactlyInAnyOrder(a, b);

        String enrollment = read(api.json(get("/v1/my/enrollments").with(as(a, acme))), "$[0].id");
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/launch", enrollment, lessonId).with(as(a, acme)));
        api.json(post("/v1/my/enrollments/{e}/lessons/{l}/complete", enrollment, lessonId).with(as(a, acme)));

        String after = api.json(get("/v1/dashboard").with(as(alice, acme)));
        assertThat((Integer) read(after, "$.kpi.completed")).isEqualTo(1);
        assertThat((Integer) read(after, "$.kpi.completionRate")).isEqualTo(50);
        assertThat((Integer) read(after, "$.courses[0].completionRate")).isEqualTo(50);

        // Another company sees only its own numbers (and its own cache entry).
        String bobAdmin = "carl-" + api.run;
        String beta = api.company("beta", bobAdmin);
        assertThat((Integer) read(api.json(get("/v1/dashboard").with(as(bobAdmin, beta))), "$.kpi.enrolled")).isZero();
        assertThat(api.raw(get("/v1/dashboard").with(as(a, acme))).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("감사 로그: 게시·배정·역할 변경·수료가 행위자와 함께 남는다")
    void auditLog() throws Exception {
        String dana = learnerIn("dana");
        api.json(patch("/v1/members/{id}", membershipId(dana)).with(as(alice, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"INSTRUCTOR\"}"));
        assign("{\"courseId\":\"" + courseId + "\",\"departmentIds\":[\"" + dev + "\"]}");

        String log = api.json(get("/v1/audit-log").with(as(alice, acme)));
        List<String> actions = read(log, "$[*].action");
        assertThat(actions).contains("COURSE_PUBLISHED", "MEMBER_ROLE_CHANGED", "COURSE_ASSIGNED", "MEMBER_DEPARTMENT_CHANGED");
        assertThat((List<String>) read(log, "$[?(@.action == 'MEMBER_ROLE_CHANGED')].actorName")).containsExactly(alice);
    }

    @Test
    @DisplayName("MediaConvert 완료 이벤트(EventBridge → SQS)를 받으면 영상이 HLS로 READY, 오류면 FAILED")
    void transcodeEvents() throws Exception {
        UUID tenantId = jdbc.sql("select id from tenant where slug = ?").param(acme).query(UUID.class).single();
        String ok = processingVideo(tenantId);
        String bad = processingVideo(tenantId);

        try (SqsClient sqs = SqsClient.builder().endpointOverride(URI.create(AsyncContainers.sqsEndpoint()))
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"))).build()) {
            String url = sqs.getQueueUrl(r -> r.queueName("lms-transcode")).queueUrl();
            sqs.sendMessage(r -> r.queueUrl(url).messageBody(event(tenantId, ok, "COMPLETE")));
            sqs.sendMessage(r -> r.queueUrl(url).messageBody(event(tenantId, bad, "ERROR")));
        }
        await("READY", () -> "READY".equals(videoStatus(ok)));
        await("FAILED", () -> "FAILED".equals(videoStatus(bad)));
        String video = api.json(get("/v1/videos/{id}", ok).with(as(ian, acme)));
        assertThat((Integer) read(video, "$.durationSeconds")).isEqualTo(125);
        String manifest = TenantContext.callAs(tenantId, () -> tx.execute(s ->
                jdbc.sql("select hls_manifest_key from video_asset where id = ?").param(UUID.fromString(ok)).query(String.class).single()));
        assertThat(manifest).isEqualTo("tenants/" + tenantId + "/videos/" + ok + "/hls/index.m3u8");
        assertThat((String) read(api.json(get("/v1/videos/{id}", bad).with(as(ian, acme))), "$.failureReason")).contains("변환 실패");
    }

    // --- helpers ------------------------------------------------------------------------------

    private String event(UUID tenantId, String videoId, String status) {
        return """
                {"version":"0","detail-type":"MediaConvert Job State Change","source":"aws.mediaconvert",
                 "detail":{"status":"%s","jobId":"job-1","errorMessage":"Bad input",
                   "userMetadata":{"tenantId":"%s","videoId":"%s"},
                   "outputGroupDetails":[{"outputDetails":[{"durationInMs":125300}]}]}}
                """.formatted(status, tenantId, videoId);
    }

    /** Upload a video (the fake transcoder makes it READY), then put it back to PROCESSING as if MediaConvert were running. */
    private String processingVideo(UUID tenantId) throws Exception {
        byte[] bytes = new byte[512];
        String init = api.json(post("/v1/uploads").with(as(ian, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"VIDEO_SOURCE\",\"fileName\":\"v.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":512}"));
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(read(init, "$.url")))
                        .header("Content-Type", "video/mp4").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.discarding());
        String videoId = read(api.json(post("/v1/uploads/{id}/complete", (String) read(init, "$.fileId")).with(as(ian, acme))), "$.videoId");
        await("fake transcode", () -> "READY".equals(videoStatus(videoId)));
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> jdbc.sql(
                "update video_asset set status = 'PROCESSING', hls_manifest_key = null, ready_at = null, duration_seconds = null where id = ?")
                .param(UUID.fromString(videoId)).update()));
        return videoId;
    }

    private String videoStatus(String videoId) {
        try {
            return read(api.json(get("/v1/videos/{id}", videoId).with(as(ian, acme))), "$.status");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void textCourse(String title) throws Exception {
        MockHttpServletResponse created = api.raw(post("/v1/courses").with(as(ian, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"));
        courseId = read(created.getContentAsString(StandardCharsets.UTF_8), "$.id");
        String etag = created.getHeader("ETag");
        var r = write(post("/v1/courses/{id}/sections", courseId), etag, "{\"title\":\"1장\"}");
        r = write(post("/v1/courses/{id}/sections/{s}/lessons", courseId, (String) read(r[0], "$.sections[0].id")), r[1],
                "{\"title\":\"정의\",\"type\":\"TEXT\"}");
        lessonId = read(r[0], "$.sections[0].lessons[0].id");
        r = write(patch("/v1/courses/{id}/lessons/{l}", courseId, lessonId), r[1], "{\"body\":\"개인정보란…\"}");
        write(post("/v1/courses/{id}/publish", courseId), r[1], null);
    }

    private String[] write(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String etag,
                           String json) throws Exception {
        request.with(as(ian, acme)).header("If-Match", etag);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MockHttpServletResponse r = api.raw(request);
        String body = r.getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getStatus()).as(body).isEqualTo(200);
        return new String[] {body, r.getHeader("ETag")};
    }

    private String learnerIn(String name) throws Exception {
        String sub = api.joined(alice, acme, name, "LEARNER");
        api.json(patch("/v1/members/{id}", membershipId(sub)).with(as(alice, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"departmentId\":\"" + dev + "\"}"));
        return sub;
    }

    private String membershipId(String sub) throws Exception {
        List<String> ids = read(api.json(get("/v1/members").with(as(alice, acme))), "$[?(@.email == '" + sub + "@example.com')].id");
        return ids.getFirst();
    }

    private void assign(String body) throws Exception {
        api.json(post("/v1/assignments").with(as(alice, acme)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static void await(String what, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("timed out waiting for " + what);
    }
}
