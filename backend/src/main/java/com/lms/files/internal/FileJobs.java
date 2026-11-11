package com.lms.files.internal;

import com.lms.shared.TenantContext;
import com.lms.tenancy.Companies;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Housekeeping, one instance at a time (ShedLock), company by company (tenant context per loop):
 * <ul>
 *   <li>videos stuck in PROCESSING for 2 hours → FAILED (a lost completion event must not leave
 *       a lesson "converting" forever; the instructor sees the failure and re-uploads);</li>
 *   <li>uploads still PENDING after a day → aborted on S3 (parts are billed until then) and marked.</li>
 * </ul>
 */
@Component
class FileJobs {

    private static final Logger log = LoggerFactory.getLogger(FileJobs.class);

    private final Companies companies;
    private final VideoAssetRepository videos;
    private final StoredFileRepository files;
    private final ObjectStorage storage;
    private final TransactionTemplate tx;

    FileJobs(Companies companies, VideoAssetRepository videos, StoredFileRepository files, ObjectStorage storage,
             PlatformTransactionManager txManager) {
        this.companies = companies;
        this.videos = videos;
        this.files = files;
        this.storage = storage;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "PT30M", initialDelayString = "PT2M")
    @SchedulerLock(name = "stuck-transcodes", lockAtMostFor = "PT20M")
    void failStuckTranscodes() {
        Instant cutoff = Instant.now().minus(2, ChronoUnit.HOURS);
        forEachCompany(t -> videos.findStuck(cutoff).forEach(v -> {
            v.markFailed("변환이 2시간 넘게 끝나지 않았습니다. 영상을 다시 올려 주세요.");
            log.warn("Video {} of tenant {} marked FAILED (stuck in PROCESSING)", v.getId(), t);
        }));
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @SchedulerLock(name = "abandoned-uploads", lockAtMostFor = "PT30M")
    void abortAbandonedUploads() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        forEachCompany(t -> files.findAbandoned(cutoff).forEach(f -> {
            try {
                if (f.isMultipart()) {
                    storage.abortMultipart(f.getObjectKey(), f.getMultipartUploadId());
                } else {
                    storage.delete(f.getObjectKey());
                }
            } catch (RuntimeException e) {
                log.warn("Could not clean up {} on S3 (the lifecycle rule will)", f.getObjectKey(), e);
            }
            f.markAborted();
        }));
    }

    private void forEachCompany(java.util.function.Consumer<UUID> work) {
        for (UUID tenantId : companies.allIds()) {
            try {
                TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(s -> work.accept(tenantId)));
            } catch (RuntimeException e) {
                log.error("File job failed for tenant {}", tenantId, e);
            }
        }
    }
}
