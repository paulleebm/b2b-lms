package com.lms.files.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.mediaconvert.model.ContainerType;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupType;
import software.amazon.awssdk.services.mediaconvert.model.VideoCodec;

/** The AWS-only parts that can be checked without AWS: the job we send and the cookies we sign. */
class MediaPipelineTest {

    @Test
    @DisplayName("MediaConvert 작업: 원본 → 같은 회사·영상 폴더 아래 HLS 3화질(1080/720/480) + 썸네일")
    void hlsJob() {
        JobSettings job = HlsJobSettings.build("media", "tenants/t1/files/f1/source", "t1", "v1");

        assertThat(job.inputs().getFirst().fileInput()).isEqualTo("s3://media/tenants/t1/files/f1/source");
        var hls = job.outputGroups().get(0);
        assertThat(hls.outputGroupSettings().type()).isEqualTo(OutputGroupType.HLS_GROUP_SETTINGS);
        assertThat(hls.outputGroupSettings().hlsGroupSettings().destination()).isEqualTo("s3://media/tenants/t1/videos/v1/hls/index");
        assertThat(hls.outputGroupSettings().hlsGroupSettings().segmentLength()).isEqualTo(6);
        assertThat(hls.outputs()).extracting(o -> o.videoDescription().height()).containsExactly(1080, 720, 480);
        assertThat(hls.outputs()).allSatisfy(o -> {
            assertThat(o.containerSettings().container()).isEqualTo(ContainerType.M3_U8);
            assertThat(o.videoDescription().codecSettings().codec()).isEqualTo(VideoCodec.H_264);
        });
        var thumbnail = job.outputGroups().get(1);
        assertThat(thumbnail.outputs().getFirst().videoDescription().codecSettings().codec()).isEqualTo(VideoCodec.FRAME_CAPTURE);
        assertThat(HlsJobSettings.manifestKey("t1", "v1")).isEqualTo("tenants/t1/videos/v1/hls/index.m3u8");
    }

    @Test
    @DisplayName("서명 쿠키: 3개(Policy·Signature·Key-Pair-Id), 이 영상 폴더에만 2시간 유효")
    void signedCookies() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
        var signer = new CloudFrontCookies("https://media.example.com", "K2ABC", pem, ".example.com");
        Instant now = Instant.parse("2026-11-06T00:00:00Z");

        var cookies = signer.forFolder("tenants/t1/videos/v1/hls/", now);

        assertThat(cookies).extracting(c -> c.name())
                .containsExactly("CloudFront-Policy", "CloudFront-Signature", "CloudFront-Key-Pair-Id");
        assertThat(cookies).allSatisfy(c -> {
            assertThat(c.path()).isEqualTo("/tenants/t1/videos/v1/hls/");
            assertThat(c.domain()).isEqualTo(".example.com");
            assertThat(c.expiresAt()).isEqualTo(now.plus(CloudFrontCookies.TTL));
        });
        String policy = new String(Base64.getDecoder().decode(cookies.getFirst().value()
                .replace('-', '+').replace('_', '=').replace('~', '/')), StandardCharsets.UTF_8);
        assertThat(policy).contains("https://media.example.com/tenants/t1/videos/v1/hls/*")
                .contains(String.valueOf(now.plus(CloudFrontCookies.TTL).getEpochSecond()));
        assertThat(cookies.get(2).value()).isEqualTo("K2ABC");
        assertThat(cookies.getFirst().toSetCookieHeader()).contains("Secure", "HttpOnly", "Domain=.example.com");
    }
}
