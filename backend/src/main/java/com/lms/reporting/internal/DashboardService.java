package com.lms.reporting.internal;

import com.lms.learning.LearningEvents;
import com.lms.shared.web.ApiNullable;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The HR dashboard. A read model: plain SQL aggregates across enrollments, members, departments
 * and courses ({@code JdbcClient}), because loading entities to count them would mean thousands
 * of objects and N+1 queries. RLS scopes every query to the company, like everything else.
 *
 * <p>Cached for 60 s per company. The cache key is the tenant id (never a shared key: one
 * company's numbers must not be served to another). Assignments, completions and reminders
 * evict that company's entry after they commit.
 */
@Service
class DashboardService {

    static final String CACHE = "dashboard";

    private final JdbcClient jdbc;

    DashboardService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record Kpi(long activeMembers, long enrolled, long completed, long inProgress, long notStarted, long overdue,
               long dueSoon, int completionRate) implements Serializable {
    }

    /** @param id null = members without a department */
    record GroupRow(@ApiNullable UUID id, String name, long enrolled, long completed, long overdue, int completionRate)
            implements Serializable {
    }

    record PendingRow(UUID enrollmentId, String memberName, String email, @ApiNullable String departmentName,
                      UUID courseId, String courseTitle, String status, @ApiNullable Instant dueAt, boolean overdue,
                      @ApiNullable Instant remindedAt, long lessonsDone, long lessonsTotal) implements Serializable {
    }

    record Dashboard(Kpi kpi, List<GroupRow> departments, List<GroupRow> courses, List<PendingRow> pending,
                     Instant generatedAt) implements Serializable {
    }

    @Cacheable(cacheNames = CACHE, key = "T(com.lms.shared.TenantContext).requireTenantId().toString()")
    @Transactional(readOnly = true)
    public Dashboard load() {
        Kpi kpi = jdbc.sql("""
                        select (select count(*) from membership where status = 'ACTIVE'),
                               count(*),
                               count(*) filter (where status = 'COMPLETED'),
                               count(*) filter (where status = 'IN_PROGRESS'),
                               count(*) filter (where status = 'NOT_STARTED'),
                               count(*) filter (where status <> 'COMPLETED' and due_at < now()),
                               count(*) filter (where status <> 'COMPLETED' and due_at >= now() and due_at < now() + interval '3 days')
                        from enrollment
                        """)
                .query((rs, n) -> new Kpi(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
                        rs.getLong(6), rs.getLong(7), rate(rs.getLong(3), rs.getLong(2))))
                .single();

        List<GroupRow> departments = new ArrayList<>(jdbc.sql("""
                        select d.id, d.name, count(e.id),
                               count(e.id) filter (where e.status = 'COMPLETED'),
                               count(e.id) filter (where e.status <> 'COMPLETED' and e.due_at < now())
                        from department d
                        left join membership m on m.department_id = d.id and m.status = 'ACTIVE'
                        left join enrollment e on e.membership_id = m.id
                        group by d.id, d.name
                        order by d.name
                        """)
                .query((rs, n) -> new GroupRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rate(rs.getLong(4), rs.getLong(3))))
                .list());
        GroupRow unassigned = jdbc.sql("""
                        select count(e.id),
                               count(e.id) filter (where e.status = 'COMPLETED'),
                               count(e.id) filter (where e.status <> 'COMPLETED' and e.due_at < now())
                        from enrollment e join membership m on m.id = e.membership_id
                        where m.department_id is null and m.status = 'ACTIVE'
                        """)
                .query((rs, n) -> new GroupRow(null, "부서 없음", rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rate(rs.getLong(2), rs.getLong(1))))
                .single();
        if (unassigned.enrolled() > 0) {
            departments.add(unassigned);
        }

        List<GroupRow> courses = jdbc.sql("""
                        select c.id, c.title, count(e.id),
                               count(e.id) filter (where e.status = 'COMPLETED'),
                               count(e.id) filter (where e.status <> 'COMPLETED' and e.due_at < now())
                        from course c join enrollment e on e.course_id = c.id
                        group by c.id, c.title
                        order by c.title
                        """)
                .query((rs, n) -> new GroupRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3),
                        rs.getLong(4), rs.getLong(5), rate(rs.getLong(4), rs.getLong(3))))
                .list();

        // Who to chase: unfinished, earliest deadline first. Lesson counts in the same query (no N+1).
        List<PendingRow> pending = jdbc.sql("""
                        select e.id, coalesce(u.name, u.email), u.email, d.name, c.id, c.title, e.status, e.due_at,
                               (e.due_at is not null and e.due_at < now()), e.reminded_at,
                               (select count(*) from lesson_progress p where p.enrollment_id = e.id and p.completed_at is not null),
                               (select count(*) from lesson l where l.course_id = e.course_id)
                        from enrollment e
                        join membership m on m.id = e.membership_id and m.status = 'ACTIVE'
                        join app_user u on u.id = m.user_id
                        left join department d on d.id = m.department_id
                        join course c on c.id = e.course_id
                        where e.status <> 'COMPLETED'
                        order by e.due_at asc nulls last, u.name
                        limit 100
                        """)
                .query((rs, n) -> new PendingRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getBoolean(9),
                        rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(), rs.getLong(11), rs.getLong(12)))
                .list();

        return new Dashboard(kpi, departments, courses, pending, Instant.now());
    }

    private static int rate(long done, long total) {
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }
}

@RestController
@PreAuthorize("hasRole('ORG_ADMIN')")
class DashboardController {

    private final DashboardService dashboard;

    DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/v1/dashboard")
    DashboardService.Dashboard load() {
        return dashboard.load();
    }
}

/** Fresh numbers right after the things that change them (otherwise the 60 s TTL applies). */
@Component
class DashboardCacheEvictor {

    private final CacheManager caches;

    DashboardCacheEvictor(CacheManager caches) {
        this.caches = caches;
    }

    @TransactionalEventListener
    void on(LearningEvents.EnrollmentsAssigned e) {
        evict(e.tenantId());
    }

    @TransactionalEventListener
    void on(LearningEvents.CourseCompleted e) {
        evict(e.tenantId());
    }

    @TransactionalEventListener
    void on(LearningEvents.RemindersRequested e) {
        evict(e.tenantId());
    }

    private void evict(UUID tenantId) {
        var cache = caches.getCache(DashboardService.CACHE);
        if (cache != null) {
            cache.evict(tenantId.toString());
        }
    }
}
