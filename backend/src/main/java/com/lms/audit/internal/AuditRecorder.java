package com.lms.audit.internal;

import com.lms.catalog.CourseStatusChanged;
import com.lms.learning.LearningEvents;
import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import com.lms.shared.web.TraceIdFilter;
import com.lms.tenancy.MemberUpdated;
import com.lms.tenancy.MembersInvited;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Audit trail from domain events, written in the same transaction as the change it describes:
 * a role change that rolls back leaves no audit row, and one that commits always has one.
 * Modules publish what happened; none of them knows the audit log exists.
 * {@code audit_log} is append-only for the application role (no UPDATE / DELETE grant).
 */
@Component
class AuditRecorder {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    AuditRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    void on(MembersInvited e) {
        record(e.tenantId(), "MEMBERS_INVITED", "MEMBER", null, Map.of("count", e.count(), "role", e.role()));
    }

    @EventListener
    void on(MemberUpdated e) {
        record(e.tenantId(), "MEMBER_" + e.change() + "_CHANGED", "MEMBER", e.membershipId(),
                Map.of("from", String.valueOf(e.from()), "to", String.valueOf(e.to())));
    }

    @EventListener
    void on(CourseStatusChanged e) {
        record(e.tenantId(), "COURSE_" + e.status(), "COURSE", e.courseId(), Map.of("title", e.title()));
    }

    @EventListener
    void on(LearningEvents.EnrollmentsAssigned e) {
        record(e.tenantId(), "COURSE_ASSIGNED", "COURSE", e.courseId(),
                Map.of("title", e.courseTitle(), "newEnrollments", e.membershipIds().size()));
    }

    @EventListener
    void on(LearningEvents.RemindersRequested e) {
        record(e.tenantId(), "REMINDERS_SENT", "ENROLLMENT", null, Map.of("count", e.reminders().size()));
    }

    @EventListener
    void on(LearningEvents.CourseCompleted e) {
        record(e.tenantId(), "COURSE_COMPLETED", "ENROLLMENT", e.learner().enrollmentId(),
                Map.of("title", e.learner().courseTitle(), "certificate", e.certificateSerial()));
    }

    private void record(UUID tenantId, String action, String targetType, UUID targetId, Map<String, Object> metadata) {
        jdbc.sql("""
                        insert into audit_log (id, tenant_id, actor_user_id, action, target_type, target_id, metadata, trace_id)
                        values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?)
                        """)
                .params(UuidV7.generate(), tenantId, TenantContext.userId().orElse(null), action, targetType, targetId,
                        JSON.writeValueAsString(metadata), MDC.get(TraceIdFilter.MDC_KEY))
                .update();
    }
}
