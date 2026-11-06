package com.lms.learning.internal;

import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Creates enrollments with one SQL statement per person, letting the database resolve overlaps:
 * <pre>
 * INSERT … ON CONFLICT (membership_id, course_id) DO UPDATE SET due_at = LEAST(old, new)
 * </pre>
 * "Select, then insert if missing" breaks under concurrency (two admins assigning at once both
 * see "missing"). The unique constraint cannot be raced. LEAST ignores NULL, so a dated
 * assignment tightens an undated enrollment, and the earlier date always wins. Completed
 * enrollments are left alone.
 */
@Component
class EnrollmentWriter {

    enum Result { CREATED, EXISTING }

    private final JdbcClient jdbc;

    EnrollmentWriter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Result upsert(UUID membershipId, UUID courseId, Instant dueAt) {
        Boolean inserted = jdbc.sql("""
                        insert into enrollment (id, tenant_id, membership_id, course_id, status, due_at, created_at)
                        values (?, ?, ?, ?, 'NOT_STARTED', ?, now())
                        on conflict (membership_id, course_id) do update
                            set due_at = least(enrollment.due_at, excluded.due_at)
                            where enrollment.status <> 'COMPLETED'
                        returning (xmax = 0) as inserted
                        """)
                .params(UuidV7.generate(), TenantContext.requireTenantId(), membershipId, courseId,
                        dueAt == null ? null : Timestamp.from(dueAt))
                .query(Boolean.class)
                .optional()
                .orElse(false);   // no row returned: it exists and is COMPLETED
        return Boolean.TRUE.equals(inserted) ? Result.CREATED : Result.EXISTING;
    }

    /** Creates the lesson's progress row if missing (two first launches at once cannot both insert). */
    void ensureProgress(UUID enrollmentId, UUID lessonId) {
        jdbc.sql("""
                        insert into lesson_progress (id, tenant_id, enrollment_id, lesson_id)
                        values (?, ?, ?, ?)
                        on conflict (enrollment_id, lesson_id) do nothing
                        """)
                .params(UuidV7.generate(), TenantContext.requireTenantId(), enrollmentId, lessonId)
                .update();
    }
}
