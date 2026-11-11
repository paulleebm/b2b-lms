package com.lms.learning.internal;

import com.lms.catalog.CourseCatalog;
import com.lms.learning.LearningEvents;
import com.lms.shared.UuidV7;
import com.lms.organization.DepartmentDirectory;
import com.lms.shared.TenantContext;
import com.lms.shared.web.ApiException;
import com.lms.shared.web.ApiNullable;
import com.lms.tenancy.MemberDirectory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Assigning courses (docs/05 §7). An assignment records the admin's intent; enrollments are the
 * per-person facts it fans out into. Everything — assignments, enrollments and the idempotency
 * record — commits in one transaction.
 */
@Service
class AssignmentService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCOPE = "assignments";

    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final EnrollmentWriter writer;
    private final CourseCatalog catalog;
    private final DepartmentDirectory departments;
    private final MemberDirectory members;
    private final JdbcClient jdbc;
    private final org.springframework.context.ApplicationEventPublisher events;

    AssignmentService(AssignmentRepository assignments, EnrollmentRepository enrollments, EnrollmentWriter writer,
                      CourseCatalog catalog, DepartmentDirectory departments, MemberDirectory members, JdbcClient jdbc,
                      org.springframework.context.ApplicationEventPublisher events) {
        this.events = events;
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.writer = writer;
        this.catalog = catalog;
        this.departments = departments;
        this.members = members;
        this.jdbc = jdbc;
    }

    record AssignCommand(UUID courseId, List<UUID> departmentIds, List<UUID> membershipIds, @ApiNullable Instant dueAt) {
    }

    /**
     * @param targeted   distinct ACTIVE people covered by this request
     * @param created    new enrollments
     * @param existing   people who already had the course (due date tightened if earlier)
     * @param notActive  named members who are invited or disabled: enrolled when they join, not now
     */
    record AssignResult(List<UUID> assignmentIds, int targeted, int created, int existing, int notActive) {
    }

    /**
     * Same key + same request → the stored result, without doing anything again.
     * Same key + different request → 422: the client reused a key by mistake.
     */
    @Transactional
    AssignResult assign(AssignCommand cmd, String idempotencyKey) {
        String hash = hash(cmd);
        if (idempotencyKey != null) {
            Optional<AssignResult> replay = replay(idempotencyKey, hash);
            if (replay.isPresent()) {
                return replay.get();
            }
        }

        CourseCatalog.CourseOutline course = catalog.assignable(cmd.courseId())
                .orElseThrow(() -> ApiException.unprocessable("COURSE_NOT_ASSIGNABLE", "게시된 코스만 배정할 수 있습니다."));
        Set<UUID> departmentIds = new LinkedHashSet<>(cmd.departmentIds());
        Set<UUID> memberIds = new LinkedHashSet<>(cmd.membershipIds());
        if (departmentIds.isEmpty() && memberIds.isEmpty()) {
            throw ApiException.badRequest("NO_TARGET", "배정할 부서나 멤버를 하나 이상 고르세요.");
        }
        Map<UUID, String> departmentNames = departments.names(departmentIds);
        if (departmentNames.size() != departmentIds.size()) {
            throw ApiException.notFound("DEPARTMENT_NOT_FOUND", "부서를 찾을 수 없습니다.");
        }
        Map<UUID, String> memberStatuses = members.statuses(memberIds);
        if (memberStatuses.size() != memberIds.size()) {
            throw ApiException.notFound("MEMBER_NOT_FOUND", "멤버를 찾을 수 없습니다.");
        }

        UUID assignedBy = TenantContext.requireMembershipId();
        List<UUID> created = new ArrayList<>();
        for (UUID d : departmentIds) {
            created.add(assignments.save(Assignment.toDepartment(cmd.courseId(), d, cmd.dueAt(), assignedBy)).getId());
        }
        for (UUID m : memberIds) {
            created.add(assignments.save(Assignment.toMember(cmd.courseId(), m, cmd.dueAt(), assignedBy)).getId());
        }

        Set<UUID> targets = Stream.concat(members.activeMembersOf(departmentIds).stream(),
                        members.activeAmong(memberIds).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UUID> newlyEnrolled = new ArrayList<>();
        for (UUID membershipId : targets) {
            if (writer.upsert(membershipId, cmd.courseId(), cmd.dueAt()) == EnrollmentWriter.Result.CREATED) {
                newlyEnrolled.add(membershipId);
            }
        }
        int newOnes = newlyEnrolled.size();
        if (!newlyEnrolled.isEmpty()) {
            // Outbox: committed with the enrollments, mailed by the worker after commit.
            events.publishEvent(new LearningEvents.EnrollmentsAssigned(UuidV7.generate(), TenantContext.requireTenantId(),
                    course.id(), course.title(), cmd.dueAt(), newlyEnrolled, assignedBy));
        }
        int notActive = (int) memberStatuses.values().stream().filter(s -> !"ACTIVE".equals(s)).count();
        AssignResult result = new AssignResult(created, targets.size(), newOnes, targets.size() - newOnes, notActive);

        if (idempotencyKey != null) {
            // Same transaction as the work: both commit, or neither does. A concurrent request with
            // the same key collides on the primary key and rolls back entirely (see the controller).
            jdbc.sql("insert into idempotency_record (tenant_id, scope, key, request_hash, response) values (?, ?, ?, ?, cast(? as jsonb))")
                    .params(TenantContext.requireTenantId(), SCOPE, idempotencyKey, hash, JSON.writeValueAsString(result))
                    .update();
        }
        return result;
    }

    @Transactional(readOnly = true)
    Optional<AssignResult> replay(String key, String hash) {
        return jdbc.sql("select request_hash, response::text from idempotency_record where scope = ? and key = ?")
                .params(SCOPE, key)
                .query((rs, n) -> {
                    if (!rs.getString(1).equals(hash)) {
                        throw ApiException.unprocessable("IDEMPOTENCY_KEY_REUSED",
                                "같은 Idempotency-Key로 다른 요청을 보냈습니다. 새 요청에는 새 키를 쓰세요.");
                    }
                    return JSON.readValue(rs.getString(2), AssignResult.class);
                })
                .optional();
    }

    // --- listing ----------------------------------------------------------------------------------

    record AssignmentView(UUID id, UUID courseId, String courseTitle, Assignment.TargetType targetType,
                          UUID targetId, @ApiNullable String targetName, @ApiNullable Instant dueAt, Instant createdAt,
                          long courseEnrolled, long courseCompleted) {
    }

    @Transactional(readOnly = true)
    List<AssignmentView> list() {
        List<Assignment> all = assignments.findAllByOrderByCreatedAtDesc();
        Set<UUID> courseIds = all.stream().map(Assignment::getCourseId).collect(Collectors.toSet());
        Map<UUID, CourseCatalog.CourseHeader> headers = catalog.headers(courseIds);
        Map<UUID, EnrollmentRepository.CourseCount> counts = courseIds.isEmpty() ? Map.of()
                : enrollments.countByCourse(courseIds).stream()
                        .collect(Collectors.toMap(EnrollmentRepository.CourseCount::getCourseId, c -> c));
        Map<UUID, String> departmentNames = departments.names(all.stream()
                .map(Assignment::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, String> memberNames = members.displayNames(all.stream()
                .map(Assignment::getMembershipId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return all.stream().map(a -> {
            boolean dept = a.getTargetType() == Assignment.TargetType.DEPARTMENT;
            UUID target = dept ? a.getDepartmentId() : a.getMembershipId();
            var count = counts.get(a.getCourseId());
            var header = headers.get(a.getCourseId());
            return new AssignmentView(a.getId(), a.getCourseId(), header == null ? "-" : header.title(),
                    a.getTargetType(), target, dept ? departmentNames.get(target) : memberNames.get(target),
                    a.getDueAt(), a.getCreatedAt(), count == null ? 0 : count.getEnrolled(),
                    count == null ? 0 : count.getCompleted());
        }).toList();
    }

    // --- new hires and department moves -----------------------------------------------------------

    /**
     * Gives a member every course that applies to them now: their department's assignments and
     * their own. Called when an invitation is accepted and when an active member changes
     * department. Existing enrollments stay (moving department does not take courses away).
     */
    @Transactional
    int syncFor(UUID membershipId) {
        UUID departmentId = members.departmentOf(membershipId).orElse(null);
        List<Assignment> applicable = assignments.findApplicable(departmentId, membershipId);
        int created = 0;
        for (Assignment a : applicable) {
            var course = catalog.assignable(a.getCourseId());
            if (course.isEmpty()) {
                continue;   // archived since: no new enrollments
            }
            if (writer.upsert(membershipId, a.getCourseId(), a.getDueAt()) == EnrollmentWriter.Result.CREATED) {
                created++;
                events.publishEvent(new LearningEvents.EnrollmentsAssigned(UuidV7.generate(), TenantContext.requireTenantId(),
                        a.getCourseId(), course.get().title(), a.getDueAt(), List.of(membershipId), null));
            }
        }
        return created;
    }

    static String hash(AssignCommand cmd) {
        String canonical = cmd.courseId() + "|" + sorted(cmd.departmentIds()) + "|" + sorted(cmd.membershipIds())
                + "|" + cmd.dueAt();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> sorted(Collection<UUID> ids) {
        return ids.stream().distinct().map(UUID::toString).sorted().toList();
    }
}
