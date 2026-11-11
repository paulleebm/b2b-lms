package com.lms.learning.internal;

import com.lms.files.MediaLibrary;
import com.lms.learning.LearningEvents;
import com.lms.shared.TenantContext;
import com.lms.shared.messaging.MessageHandler;
import com.lms.shared.web.ApiException;
import com.lms.tenancy.Companies;
import com.lms.tenancy.MemberDirectory;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Certificates after completion. The row (with its serial) was created in the completion
 * transaction; the PDF is produced here, on the worker, from the outbox message — rendering takes
 * a second or two and must not hold up (or fail) the learner's quiz submission.
 */
@Service
class CertificateService implements MessageHandler {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DateTimeFormatter KOREAN_DATE =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일").withZone(ZoneId.of("Asia/Seoul"));

    private final CertificateRepository certificates;
    private final EnrollmentRepository enrollments;
    private final CertificateRenderer renderer;
    private final MediaLibrary media;
    private final MemberDirectory members;
    private final Companies companies;
    private final JdbcClient jdbc;
    private final String appUrl;

    CertificateService(CertificateRepository certificates, EnrollmentRepository enrollments, CertificateRenderer renderer,
                       MediaLibrary media, MemberDirectory members, Companies companies, JdbcClient jdbc,
                       @Value("${lms.keycloak.app-url}") String appUrl) {
        this.certificates = certificates;
        this.enrollments = enrollments;
        this.renderer = renderer;
        this.media = media;
        this.members = members;
        this.companies = companies;
        this.jdbc = jdbc;
        this.appUrl = appUrl;
    }

    // --- worker -------------------------------------------------------------------------------

    @Override
    public String messageType() {
        return LearningEvents.CourseCompleted.TYPE;
    }

    @Override
    public String name() {
        return "certificate-pdf";
    }

    /** Idempotent: a certificate that already has its PDF is left alone. */
    @Override
    public void handle(MessageEnvelope message) {
        var event = JSON.treeToValue(message.payload(), LearningEvents.CourseCompleted.class);
        Certificate certificate = certificates.findByEnrollmentId(event.learner().enrollmentId())
                .orElseThrow(() -> new IllegalStateException("No certificate row for " + event.learner().enrollmentId()));
        render(certificate, event.membershipId(), event.learner().courseTitle(), message.tenantId());
    }

    private void render(Certificate certificate, UUID membershipId, String courseTitle, UUID tenantId) {
        if (certificate.getObjectKey() != null) {
            return;
        }
        String holder = members.displayNames(List.of(membershipId)).getOrDefault(membershipId, "-");
        byte[] pdf = renderer.render(new CertificateRenderer.CertificateData(
                companies.name(tenantId).orElse(""), holder, courseTitle,
                KOREAN_DATE.format(certificate.getIssuedAt()), certificate.getSerial(),
                appUrl + "/verify/" + certificate.getSerial()));
        certificate.attachPdf(media.storeCertificate(certificate.getEnrollmentId(), pdf));
        certificates.save(certificate);
    }

    /**
     * Safety net for lost or failed messages (and certificates issued before the outbox existed):
     * certificates still without a PDF a few minutes after issue are rendered here.
     */
    @Transactional
    int renderMissing(java.util.function.Function<UUID, String> courseTitles) {
        int rendered = 0;
        for (Certificate c : certificates.findMissingPdf(Instant.now().minusSeconds(120))) {
            Enrollment e = enrollments.findById(c.getEnrollmentId()).orElseThrow();
            render(c, e.getMembershipId(), courseTitles.apply(e.getCourseId()), TenantContext.requireTenantId());
            rendered++;
        }
        return rendered;
    }

    // --- learner ------------------------------------------------------------------------------

    record CertificateLink(String serial, Instant issuedAt, String url) {
    }

    @Transactional(readOnly = true)
    CertificateLink myCertificate(UUID enrollmentId) {
        Enrollment e = enrollments.findByIdAndMembershipId(enrollmentId, TenantContext.requireMembershipId())
                .orElseThrow(() -> ApiException.notFound("ENROLLMENT_NOT_FOUND", "수강 정보를 찾을 수 없습니다."));
        Certificate c = certificates.findByEnrollmentId(e.getId())
                .orElseThrow(() -> ApiException.notFound("CERTIFICATE_NOT_FOUND", "아직 수료하지 않았습니다."));
        if (c.getObjectKey() == null) {
            throw ApiException.conflict("CERTIFICATE_NOT_READY", "수료증을 만드는 중입니다. 잠시 후 다시 시도하세요.");
        }
        return new CertificateLink(c.getSerial(), c.getIssuedAt(),
                media.downloadUrl(c.getObjectKey(), "수료증-" + c.getSerial() + ".pdf"));
    }

    // --- public check ------------------------------------------------------------------------

    record Verification(String serial, String holderName, String courseTitle, String companyName, Instant issuedAt) {
    }

    /** Anonymous: runs without a tenant; the SECURITY DEFINER function returns only these fields. */
    Optional<Verification> verify(String serial) {
        return jdbc.sql("select serial, holder_name, course_title, company_name, issued_at from verify_certificate(?)")
                .param(serial)
                .query((rs, n) -> new Verification(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant()))
                .optional();
    }
}
