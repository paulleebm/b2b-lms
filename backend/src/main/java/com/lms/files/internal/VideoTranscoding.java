package com.lms.files.internal;

import com.lms.shared.TenantContext;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns an uploaded source video into adaptive HLS.
 *
 * <p>AWS: {@code MediaConvertTranscoder} submits a job (1080p/720p/480p HLS, output to
 * {@code tenants/{t}/videos/{id}/hls/}); completion arrives as an EventBridge event → SQS →
 * {@link VideoStateUpdater}. Locally there is no MediaConvert, so {@link FakeVideoTranscoder}
 * reports success after a delay — same state machine, same code path after the job.
 */
interface VideoTranscoder {

    /** @return the provider's job id */
    String submit(UUID tenantId, UUID videoId, String sourceKey);
}

@Component
class VideoTranscodeTrigger {

    private final VideoTranscoder transcoder;
    private final VideoStateUpdater updater;

    VideoTranscodeTrigger(VideoTranscoder transcoder, VideoStateUpdater updater) {
        this.transcoder = transcoder;
        this.updater = updater;
    }

    @TransactionalEventListener
    void on(UploadService.VideoSourceUploaded event) {
        try {
            String jobId = transcoder.submit(event.tenantId(), event.videoId(), event.sourceKey());
            updater.jobSubmitted(event.tenantId(), event.videoId(), jobId);
        } catch (RuntimeException e) {
            updater.failed(event.tenantId(), event.videoId(), "변환 작업을 시작하지 못했습니다.");
        }
    }
}

/** Applies transcoder results. Runs outside any request, so it binds the tenant itself. */
@Service
class VideoStateUpdater {

    private final VideoAssetRepository videos;
    private final TransactionTemplate tx;

    VideoStateUpdater(VideoAssetRepository videos, PlatformTransactionManager txManager) {
        this.videos = videos;
        this.tx = new TransactionTemplate(txManager);
        // Called from an after-commit listener too, where the finished transaction's resources are
        // still bound to the thread: always start a fresh one on a fresh (tenant-bound) connection.
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void jobSubmitted(UUID tenantId, UUID videoId, String jobId) {
        apply(tenantId, videoId, v -> v.jobSubmitted(jobId));
    }

    void ready(UUID tenantId, UUID videoId, String manifestKey, Integer durationSeconds) {
        apply(tenantId, videoId, v -> v.markReady(manifestKey, durationSeconds));
    }

    void failed(UUID tenantId, UUID videoId, String reason) {
        apply(tenantId, videoId, v -> v.markFailed(reason));
    }

    private void apply(UUID tenantId, UUID videoId, java.util.function.Consumer<VideoAsset> change) {
        TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s ->
                videos.findById(videoId).ifPresent(change)));
    }
}

@Component
@ConditionalOnProperty(name = "lms.media.transcoder", havingValue = "fake", matchIfMissing = true)
class FakeVideoTranscoder implements VideoTranscoder {

    private static final Logger log = LoggerFactory.getLogger(FakeVideoTranscoder.class);

    private final VideoStateUpdater updater;
    private final Duration delay;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("fake-transcoder").factory());

    FakeVideoTranscoder(VideoStateUpdater updater, @Value("${lms.media.fake-transcode-delay:3s}") Duration delay) {
        this.updater = updater;
        this.delay = delay;
    }

    @Override
    public String submit(UUID tenantId, UUID videoId, String sourceKey) {
        String jobId = "fake-" + videoId;
        scheduler.schedule(() -> {
            try {
                // Locally the "rendition" is the source itself; the player handles MP4 too.
                updater.ready(tenantId, videoId, sourceKey, null);
            } catch (RuntimeException e) {
                log.warn("Fake transcode of {} failed", videoId, e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
        return jobId;
    }
}
