package com.lms.learning.internal;

import com.lms.catalog.CourseCatalog;
import com.lms.shared.TenantContext;
import com.lms.tenancy.Companies;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every 10 minutes, company by company: render certificates whose PDF never arrived. */
@Component
class CertificateBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(CertificateBackfillJob.class);

    private final CertificateService certificates;
    private final CourseCatalog catalog;
    private final Companies companies;

    CertificateBackfillJob(CertificateService certificates, CourseCatalog catalog, Companies companies) {
        this.certificates = certificates;
        this.catalog = catalog;
        this.companies = companies;
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    @SchedulerLock(name = "certificate-backfill", lockAtMostFor = "PT9M")
    void run() {
        for (UUID tenantId : companies.allIds()) {
            try {
                int n = TenantContext.callAs(tenantId, () -> certificates.renderMissing(courseId ->
                        catalog.headers(List.of(courseId)).getOrDefault(courseId,
                                new CourseCatalog.CourseHeader(courseId, "", "", "", 0)).title()));
                if (n > 0) {
                    log.info("Rendered {} missing certificate PDF(s) for tenant {}", n, tenantId);
                }
            } catch (RuntimeException e) {
                log.error("Certificate backfill failed for tenant {}", tenantId, e);
            }
        }
    }
}
