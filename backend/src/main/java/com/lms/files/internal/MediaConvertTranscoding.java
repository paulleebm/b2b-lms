package com.lms.files.internal;

import com.lms.shared.messaging.RawQueueHandler;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * AWS MediaConvert (lms.media.transcoder=mediaconvert). The job carries tenantId and videoId as
 * user metadata; MediaConvert reports completion as an EventBridge event, which a rule forwards to
 * the transcode queue (Terraform: modules/async) — no polling.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "lms.media.transcoder", havingValue = "mediaconvert")
class MediaConvertConfig {

    @Bean(destroyMethod = "close")
    MediaConvertClient mediaConvertClient(StorageProperties props) {
        return MediaConvertClient.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    @Bean
    VideoTranscoder mediaConvertTranscoder(MediaConvertClient client, StorageProperties props,
                                           @Value("${lms.media.mediaconvert.role-arn}") String roleArn,
                                           @Value("${lms.media.mediaconvert.queue-arn}") String queueArn) {
        return (tenantId, videoId, sourceKey) -> client.createJob(r -> r
                        .role(roleArn)
                        .queue(queueArn)
                        .userMetadata(Map.of("tenantId", tenantId.toString(), "videoId", videoId.toString()))
                        .settings(HlsJobSettings.build(props.bucket(), sourceKey, tenantId.toString(), videoId.toString())))
                .job().id();
    }
}

/**
 * "MediaConvert Job State Change" events (via EventBridge → SQS). Idempotent: VideoAsset ignores a
 * second COMPLETE, and ERROR after READY. Events of other jobs (no user metadata) are ignored.
 */
@Component
class TranscodeEventsHandler implements RawQueueHandler {

    private static final Logger log = LoggerFactory.getLogger(TranscodeEventsHandler.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final VideoStateUpdater updater;

    TranscodeEventsHandler(VideoStateUpdater updater) {
        this.updater = updater;
    }

    @Override
    public String queue() {
        return "transcode";
    }

    @Override
    public void handle(String body) {
        JsonNode detail = JSON.readTree(body).path("detail");
        JsonNode meta = detail.path("userMetadata");
        if (meta.path("tenantId").isMissingNode() || meta.path("videoId").isMissingNode()) {
            log.debug("Ignoring a MediaConvert event without our metadata");
            return;
        }
        UUID tenantId = UUID.fromString(meta.path("tenantId").asString());
        UUID videoId = UUID.fromString(meta.path("videoId").asString());
        switch (detail.path("status").asString()) {
            case "COMPLETE" -> {
                JsonNode duration = detail.path("outputGroupDetails").path(0).path("outputDetails").path(0).path("durationInMs");
                Integer seconds = duration.isNumber() ? (int) Math.round(duration.asLong() / 1000.0) : null;
                updater.ready(tenantId, videoId, HlsJobSettings.manifestKey(tenantId.toString(), videoId.toString()), seconds);
            }
            case "ERROR" -> updater.failed(tenantId, videoId,
                    "변환 실패: " + detail.path("errorMessage").asString("알 수 없는 오류"));
            default -> log.debug("Ignoring MediaConvert status {}", detail.path("status").asString());
        }
    }
}
