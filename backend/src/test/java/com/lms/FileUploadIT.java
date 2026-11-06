package com.lms;

import static com.lms.support.Api.as;
import static com.lms.support.Api.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.lms.support.Api;
import com.lms.support.IntegrationTest;
import com.lms.tenancy.internal.KeycloakAdminClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Direct-to-S3 uploads against a real S3 API (S3Mock): the API hands out URLs, the "browser"
 * (java.net.http here) puts the bytes, and the API refuses to trust anything it has not checked.
 */
class FileUploadIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KeycloakAdminClient keycloak;

    final HttpClient http = HttpClient.newHttpClient();
    Api api;
    String acme;
    String alice = "alice";
    String instructor;

    @BeforeEach
    void setUp() throws Exception {
        api = new Api(mvc, keycloak);
        alice = "alice-" + api.run;
        acme = api.company("acme", alice);
        instructor = api.joined(alice, acme, "ian", "INSTRUCTOR");
    }

    @Test
    @DisplayName("작은 문서: presigned PUT 한 번 → complete에서 크기·형식 확인 → 다운로드 링크")
    void singlePutDocument() throws Exception {
        byte[] pdf = "%PDF-1.7 tiny".getBytes();
        String init = init(instructor, "DOCUMENT", "../../안내서.pdf", "application/pdf", pdf.length);
        assertThat((String) read(init, "$.mode")).isEqualTo("SINGLE");
        String fileId = read(init, "$.fileId");

        assertThat(put(read(init, "$.url"), "application/pdf", pdf).statusCode()).isEqualTo(200);

        String done = api.json(post("/v1/uploads/{id}/complete", fileId).with(as(instructor, acme)));
        assertThat((String) read(done, "$.status")).isEqualTo("UPLOADED");

        String link = api.json(get("/v1/files/{id}/download-url", fileId).with(as(alice, acme)));
        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(read(link, "$.url"))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.body()).isEqualTo(pdf);
        // The client's path was dropped; only the base name is kept for display/download.
        assertThat(download.headers().firstValue("Content-Disposition").orElse("")).contains("%EC%95%88%EB%82%B4%EC%84%9C.pdf");
    }

    @Test
    @DisplayName("신고한 크기와 다른 파일이 올라오면 complete가 거부하고 객체를 지운다")
    void sizeMismatchIsRejected() throws Exception {
        String init = init(instructor, "DOCUMENT", "a.pdf", "application/pdf", 100);
        put(read(init, "$.url"), "application/pdf", new byte[40]);   // S3Mock does not enforce the signed length

        var response = api.raw(post("/v1/uploads/{id}/complete", (String) read(init, "$.fileId")).with(as(instructor, acme)));
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(response.getContentAsString()).contains("UPLOAD_MISMATCH");

        var again = api.raw(post("/v1/uploads/{id}/complete", (String) read(init, "$.fileId")).with(as(instructor, acme)));
        assertThat(again.getStatus()).isEqualTo(409);   // ABORTED, cannot be completed later
    }

    @Test
    @DisplayName("허용되지 않은 형식과 너무 큰 파일은 URL을 받기 전에 거부된다")
    void typeAndSizeAreCheckedUpFront() throws Exception {
        var exe = api.raw(initRequest(instructor, "DOCUMENT", "setup.exe", "application/x-msdownload", 1000));
        assertThat(exe.getStatus()).isEqualTo(422);
        assertThat(exe.getContentAsString()).contains("UNSUPPORTED_FILE_TYPE");

        var huge = api.raw(initRequest(instructor, "DOCUMENT", "big.pdf", "application/pdf", 51L * 1024 * 1024));
        assertThat(huge.getStatus()).isEqualTo(422);
        assertThat(huge.getContentAsString()).contains("FILE_TOO_LARGE");
    }

    @Test
    @DisplayName("큰 영상: multipart 업로드 → complete → 변환(가짜) 후 READY")
    void multipartVideoBecomesReady() throws Exception {
        int size = 11 * 1024 * 1024;                       // test thresholds: > 6 MiB → multipart, 5 MiB parts
        byte[] video = new byte[size];
        Arrays.fill(video, (byte) 7);
        String init = init(instructor, "VIDEO_SOURCE", "강의1.mp4", "video/mp4", size);
        assertThat((String) read(init, "$.mode")).isEqualTo("MULTIPART");
        assertThat((Integer) read(init, "$.partCount")).isEqualTo(3);
        String fileId = read(init, "$.fileId");
        int partSize = ((Number) read(init, "$.partSizeBytes")).intValue();

        String urls = api.json(post("/v1/uploads/{id}/parts", fileId).with(as(instructor, acme))
                .contentType(MediaType.APPLICATION_JSON).content("{\"partNumbers\":[1,2,3]}"));
        List<String> parts = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            String url = read(urls, "$.parts[" + (n - 1) + "].url");
            byte[] chunk = Arrays.copyOfRange(video, (n - 1) * partSize, Math.min(size, n * partSize));
            HttpResponse<String> r = put(url, null, chunk);
            assertThat(r.statusCode()).isEqualTo(200);
            String etag = r.headers().firstValue("ETag").orElseThrow();
            parts.add("{\"partNumber\":" + n + ",\"etag\":" + quoteJson(etag) + "}");
        }

        String done = api.json(post("/v1/uploads/{id}/complete", fileId).with(as(instructor, acme))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"parts\":[" + String.join(",", parts) + "],\"durationSeconds\":754}"));
        String videoId = read(done, "$.videoId");
        assertThat(videoId).isNotNull();

        String video1 = awaitVideo(videoId, "READY");
        assertThat((Integer) read(video1, "$.durationSeconds")).isEqualTo(754);
    }

    @Test
    @DisplayName("같은 회사 사람이라도 남이 시작한 업로드는 완료·취소할 수 없다")
    void onlyTheUploaderCanFinish() throws Exception {
        String init = init(instructor, "DOCUMENT", "a.pdf", "application/pdf", 10);
        String fileId = read(init, "$.fileId");

        assertThat(api.raw(post("/v1/uploads/{id}/complete", fileId).with(as(alice, acme))).getStatus()).isEqualTo(404);
        assertThat(api.raw(delete("/v1/uploads/{id}", fileId).with(as(alice, acme))).getStatus()).isEqualTo(404);
        assertThat(api.raw(delete("/v1/uploads/{id}", fileId).with(as(instructor, acme))).getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("학습자는 업로드를 시작할 수 없고, 다른 회사의 영상·파일은 404")
    void learnersAndOtherCompaniesAreKeptOut() throws Exception {
        String learner = api.joined(alice, acme, "lee", "LEARNER");
        assertThat(api.raw(initRequest(learner, "DOCUMENT", "a.pdf", "application/pdf", 10)).getStatus()).isEqualTo(403);

        byte[] pdf = "%PDF".getBytes();
        String init = init(instructor, "DOCUMENT", "a.pdf", "application/pdf", pdf.length);
        put(read(init, "$.url"), "application/pdf", pdf);
        String fileId = read(init, "$.fileId");
        api.json(post("/v1/uploads/{id}/complete", fileId).with(as(instructor, acme)));

        String bob = "bob-" + api.run;
        String beta = api.company("beta", bob);
        assertThat(api.raw(get("/v1/files/{id}/download-url", fileId).with(as(bob, beta))).getStatus()).isEqualTo(404);
    }

    // --- helpers ------------------------------------------------------------------------------

    private String init(String sub, String purpose, String name, String type, long size) throws Exception {
        return api.json(initRequest(sub, purpose, name, type, size));
    }

    private org.springframework.test.web.servlet.RequestBuilder initRequest(String sub, String purpose, String name,
                                                                          String type, long size) {
        return post("/v1/uploads").with(as(sub, acme)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"purpose\":\"" + purpose + "\",\"fileName\":" + quoteJson(name)
                        + ",\"contentType\":\"" + type + "\",\"sizeBytes\":" + size + "}");
    }

    private HttpResponse<String> put(String url, String contentType, byte[] body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String awaitVideo(String videoId, String status) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String body;
        do {
            body = api.json(get("/v1/videos/{id}", videoId).with(as(instructor, acme)));
            if (status.equals(read(body, "$.status"))) {
                return body;
            }
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("video did not become " + status + ": " + body);
    }

    private static String quoteJson(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
