package com.lms;

import static com.lms.support.Api.as;
import static com.lms.support.Api.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.lms.support.Api;
import com.lms.support.IntegrationTest;
import com.lms.tenancy.internal.KeycloakAdminClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Course authoring: outline editing, ETag/If-Match concurrency, publish rules, ownership. */
class CourseStudioIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KeycloakAdminClient keycloak;

    Api api;
    String acme;
    String alice;      // ORG_ADMIN
    String ian;        // INSTRUCTOR
    String jin;        // another INSTRUCTOR

    /** The latest course body + ETag, as a client would keep them. */
    String course;
    String etag;

    @BeforeEach
    void setUp() throws Exception {
        api = new Api(mvc, keycloak);
        alice = "alice-" + api.run;
        acme = api.company("acme", alice);
        ian = api.joined(alice, acme, "ian", "INSTRUCTOR");
        jin = api.joined(alice, acme, "jin", "INSTRUCTOR");
    }

    @Test
    @DisplayName("코스 → 섹션 → 텍스트 레슨 → 본문 작성 → 게시. 쓸 때마다 ETag(버전)가 오른다")
    void buildAndPublish() throws Exception {
        create(ian, "신입 보안 교육");
        assertThat(etag).isEqualTo("\"0\"");

        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"1장. 계정 보안\"}");
        assertThat(etag).isEqualTo("\"1\"");
        String sectionId = read(course, "$.sections[0].id");

        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), sectionId), "{\"title\":\"비밀번호 규칙\",\"type\":\"TEXT\"}");
        String lessonId = read(course, "$.sections[0].lessons[0].id");
        assertThat((String) read(course, "$.sections[0].lessons[0].contentState")).isEqualTo("MISSING");

        write(ian, patch("/v1/courses/{id}/lessons/{l}", id(), lessonId), "{\"body\":\"# 12자 이상\"}");
        assertThat((String) read(course, "$.sections[0].lessons[0].contentState")).isEqualTo("READY");

        write(ian, post("/v1/courses/{id}/publish", id()), null);
        assertThat((String) read(course, "$.status")).isEqualTo("PUBLISHED");
        assertThat(etag).isEqualTo("\"4\"");
    }

    @Test
    @DisplayName("If-Match 없이 쓰면 428, 남이 먼저 고친 뒤의 옛 ETag면 412")
    void ifMatchIsRequiredAndChecked() throws Exception {
        create(ian, "코스");
        String stale = etag;

        MockHttpServletResponse missing = api.raw(patch("/v1/courses/{id}", id()).with(as(ian, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"A\"}"));
        assertThat(missing.getStatus()).isEqualTo(428);

        write(ian, patch("/v1/courses/{id}", id()), "{\"title\":\"먼저 고친 제목\"}");

        MockHttpServletResponse late = api.raw(patch("/v1/courses/{id}", id()).with(as(alice, acme))
                .header("If-Match", stale).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"덮어쓰기\"}"));
        assertThat(late.getStatus()).isEqualTo(412);
        assertThat(late.getContentAsString()).contains("COURSE_CHANGED");
        assertThat((String) read(api.json(get("/v1/courses/{id}", id()).with(as(ian, acme))), "$.title"))
                .isEqualTo("먼저 고친 제목");
    }

    @Test
    @DisplayName("빈 섹션·본문 없는 레슨이 있으면 게시가 422로 거부되고 고칠 곳 목록이 온다")
    void publishListsWhatIsMissing() throws Exception {
        create(ian, "미완성 코스");
        MockHttpServletResponse none = writeRaw(ian, post("/v1/courses/{id}/publish", id()), null);
        assertThat(none.getStatus()).isEqualTo(422);
        assertThat((List<String>) read(none.getContentAsString(), "$.errors[*].code")).containsExactly("NO_SECTIONS");

        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"1장\"}");
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"2장\"}");
        String first = read(course, "$.sections[0].id");
        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), first), "{\"title\":\"영상 레슨\",\"type\":\"VIDEO\"}");

        MockHttpServletResponse problems = writeRaw(ian, post("/v1/courses/{id}/publish", id()), null);
        assertThat(problems.getStatus()).isEqualTo(422);
        assertThat((List<String>) read(problems.getContentAsString(), "$.errors[*].code"))
                .containsExactlyInAnyOrder("LESSON_MISSING", "EMPTY_SECTION");
    }

    @Test
    @DisplayName("게시된 코스의 목차는 잠기고(409), 게시 취소하면 다시 고칠 수 있다")
    void publishedOutlineIsLocked() throws Exception {
        publishedTextCourse();

        MockHttpServletResponse locked = writeRaw(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"추가\"}");
        assertThat(locked.getStatus()).isEqualTo(409);
        assertThat(locked.getContentAsString()).contains("COURSE_NOT_EDITABLE");

        write(ian, patch("/v1/courses/{id}", id()), "{\"description\":\"설명은 게시 후에도 고칠 수 있다\"}");
        write(ian, post("/v1/courses/{id}/unpublish", id()), null);
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"추가\"}");
        assertThat((List<?>) read(course, "$.sections")).hasSize(2);
    }

    @Test
    @DisplayName("목차 순서 바꾸기: 섹션 순서와 섹션 간 레슨 이동을 한 번에, 목록이 다르면 409")
    void reorderOutline() throws Exception {
        create(ian, "정렬");
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"A\"}");
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"B\"}");
        String a = read(course, "$.sections[0].id");
        String b = read(course, "$.sections[1].id");
        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), a), "{\"title\":\"a1\",\"type\":\"TEXT\"}");
        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), a), "{\"title\":\"a2\",\"type\":\"TEXT\"}");
        String a1 = read(course, "$.sections[0].lessons[0].id");
        String a2 = read(course, "$.sections[0].lessons[1].id");

        // B first; a2 moves into B; a1 stays in A.
        write(ian, put("/v1/courses/{id}/outline", id()), "{\"sections\":["
                + "{\"id\":\"" + b + "\",\"lessonIds\":[\"" + a2 + "\"]},"
                + "{\"id\":\"" + a + "\",\"lessonIds\":[\"" + a1 + "\"]}]}");
        assertThat((List<String>) read(course, "$.sections[*].title")).containsExactly("B", "A");
        assertThat((List<String>) read(course, "$.sections[0].lessons[*].title")).containsExactly("a2");

        MockHttpServletResponse partial = writeRaw(ian, put("/v1/courses/{id}/outline", id()),
                "{\"sections\":[{\"id\":\"" + a + "\",\"lessonIds\":[\"" + a1 + "\"]}]}");
        assertThat(partial.getStatus()).isEqualTo(409);
        assertThat(partial.getContentAsString()).contains("OUTLINE_MISMATCH");
    }

    @Test
    @DisplayName("섹션을 지우면 남은 섹션 번호가 다시 매겨진다")
    void deleteRenumbers() throws Exception {
        create(ian, "삭제");
        for (String t : List.of("A", "B", "C")) {
            write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"" + t + "\"}");
        }
        String b = read(course, "$.sections[1].id");
        write(ian, delete("/v1/courses/{id}/sections/{s}", id(), b), null);
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"D\"}");   // would clash at position 2 if not renumbered
        assertThat((List<String>) read(course, "$.sections[*].title")).containsExactly("A", "C", "D");
    }

    @Test
    @DisplayName("강사는 자기 코스만 보고 고친다. 관리자는 모두. 학습자는 스튜디오 API 403")
    void ownership() throws Exception {
        create(ian, "이안의 코스");
        String iansCourse = id();

        assertThat((List<?>) read(api.json(get("/v1/courses").with(as(jin, acme))), "$")).isEmpty();
        assertThat(api.raw(get("/v1/courses/{id}", iansCourse).with(as(jin, acme))).getStatus()).isEqualTo(403);

        String all = api.json(get("/v1/courses").with(as(alice, acme)));
        assertThat((List<String>) read(all, "$[*].title")).containsExactly("이안의 코스");
        assertThat((String) read(all, "$[0].ownerName")).isEqualTo(ian);
        write(alice, patch("/v1/courses/{id}", iansCourse), "{\"title\":\"관리자가 고침\"}");

        String learner = api.joined(alice, acme, "lena", "LEARNER");
        assertThat(api.raw(get("/v1/courses").with(as(learner, acme))).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("다른 회사의 코스는 404, 다른 회사의 영상은 레슨에 붙일 수 없다")
    void tenantIsolation() throws Exception {
        create(ian, "ACME 코스");
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"1장\"}");
        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), (String) read(course, "$.sections[0].id")),
                "{\"title\":\"영상\",\"type\":\"VIDEO\"}");
        String lessonId = read(course, "$.sections[0].lessons[0].id");

        String bob = "bob-" + api.run;
        String beta = api.company("beta", bob);
        assertThat(api.raw(get("/v1/courses/{id}", id()).with(as(bob, beta))).getStatus()).isEqualTo(404);

        String betaVideo = uploadVideo(bob, beta);
        MockHttpServletResponse foreign = writeRaw(ian, patch("/v1/courses/{id}/lessons/{l}", id(), lessonId),
                "{\"videoId\":\"" + betaVideo + "\"}");
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(foreign.getContentAsString()).contains("VIDEO_NOT_FOUND");
    }

    @Test
    @DisplayName("영상 레슨: 업로드 → 변환 중이면 게시 불가 → READY 되면 게시 가능")
    void videoLessonPublishesOnceReady() throws Exception {
        create(ian, "영상 코스");
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"1장\"}");
        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), (String) read(course, "$.sections[0].id")),
                "{\"title\":\"오리엔테이션\",\"type\":\"VIDEO\"}");
        String lessonId = read(course, "$.sections[0].lessons[0].id");

        String videoId = uploadVideo(ian, acme);
        write(ian, patch("/v1/courses/{id}/lessons/{l}", id(), lessonId), "{\"videoId\":\"" + videoId + "\"}");

        long deadline = System.currentTimeMillis() + 10_000;
        while (!"READY".equals(read(course, "$.sections[0].lessons[0].contentState"))) {
            assertThat(System.currentTimeMillis()).as("video never became READY").isLessThan(deadline);
            Thread.sleep(100);
            refresh(ian);
        }
        assertThat((String) read(course, "$.sections[0].lessons[0].video.fileName")).isEqualTo("orientation.mp4");
        write(ian, post("/v1/courses/{id}/publish", id()), null);
        assertThat((String) read(course, "$.status")).isEqualTo("PUBLISHED");
    }

    // --- helpers ------------------------------------------------------------------------------

    private String id() {
        return read(course, "$.id");
    }

    private void create(String sub, String title) throws Exception {
        MockHttpServletResponse r = api.raw(post("/v1/courses").with(as(sub, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        course = r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        etag = r.getHeader("ETag");
    }

    private void refresh(String sub) throws Exception {
        MockHttpServletResponse r = api.raw(get("/v1/courses/{id}", id()).with(as(sub, acme)));
        course = r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        etag = r.getHeader("ETag");
    }

    /** A write with the current ETag; keeps the returned course and ETag, like the studio does. */
    private void write(String sub, MockHttpServletRequestBuilder request, String json) throws Exception {
        MockHttpServletResponse r = writeRaw(sub, request, json);
        String body = r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(r.getStatus()).as(body).isBetween(200, 299);
        if (r.getStatus() != 204) {
            course = body;
            etag = r.getHeader("ETag");
        }
    }

    private MockHttpServletResponse writeRaw(String sub, MockHttpServletRequestBuilder request, String json) throws Exception {
        request.with(as(sub, acme)).header("If-Match", etag);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return api.raw(request);
    }

    private void publishedTextCourse() throws Exception {
        create(ian, "게시된 코스");
        write(ian, post("/v1/courses/{id}/sections", id()), "{\"title\":\"1장\"}");
        write(ian, post("/v1/courses/{id}/sections/{s}/lessons", id(), (String) read(course, "$.sections[0].id")),
                "{\"title\":\"본문\",\"type\":\"TEXT\"}");
        write(ian, patch("/v1/courses/{id}/lessons/{l}", id(), (String) read(course, "$.sections[0].lessons[0].id")),
                "{\"body\":\"내용\"}");
        write(ian, post("/v1/courses/{id}/publish", id()), null);
    }

    private String uploadVideo(String sub, String org) throws Exception {
        byte[] bytes = new byte[1024];
        String init = api.json(post("/v1/uploads").with(as(sub, org)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"VIDEO_SOURCE\",\"fileName\":\"orientation.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":" + bytes.length + "}"));
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(read(init, "$.url")))
                .header("Content-Type", "video/mp4").PUT(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.discarding());
        return read(api.json(post("/v1/uploads/{id}/complete", (String) read(init, "$.fileId")).with(as(sub, org))), "$.videoId");
    }
}
