package com.lms.learning.internal;

import com.lms.catalog.CourseUsage;
import com.lms.shared.TenantContext;
import com.lms.tenancy.MemberDepartmentChanged;
import com.lms.tenancy.MemberJoined;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * New hires and department moves get the courses that apply to them (docs/05 §7).
 *
 * <p>Runs after the tenancy change commits, in its own transaction: if enrollment creation failed
 * it must not undo someone's sign-in. A failure is logged; the next department change or a
 * re-run repairs it (the upsert is idempotent). Day 4 moves this onto the outbox for retries.
 */
@Component
class EnrollmentSync implements CourseUsage {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentSync.class);

    private final AssignmentService assignments;
    private final EnrollmentRepository enrollments;
    private final TransactionTemplate tx;

    EnrollmentSync(AssignmentService assignments, EnrollmentRepository enrollments, PlatformTransactionManager txManager) {
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener
    void on(MemberJoined event) {
        sync(event.tenantId(), event.membershipId());
    }

    @TransactionalEventListener
    void on(MemberDepartmentChanged event) {
        sync(event.tenantId(), event.membershipId());
    }

    private void sync(UUID tenantId, UUID membershipId) {
        try {
            Integer created = TenantContext.callAs(tenantId, () -> tx.execute(s -> assignments.syncFor(membershipId)));
            if (created != null && created > 0) {
                log.info("Enrolled member {} in {} course(s) from existing assignments", membershipId, created);
            }
        } catch (RuntimeException e) {
            log.error("Enrollment sync failed for member {}", membershipId, e);
        }
    }

    @Override
    public boolean hasEnrollments(UUID courseId) {
        return enrollments.existsByCourseId(courseId);
    }
}
