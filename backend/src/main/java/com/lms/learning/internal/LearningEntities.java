package com.lms.learning.internal;

import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/** The admin's intent: "this department / this person takes this course, by this date". */
@Entity
@Table(name = "assignment")
class Assignment extends AbstractEntity {

    enum TargetType { DEPARTMENT, MEMBER }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private TargetType targetType;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "membership_id", updatable = false)
    private UUID membershipId;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Assignment() {
    }

    static Assignment toDepartment(UUID courseId, UUID departmentId, Instant dueAt, UUID assignedBy) {
        Assignment a = new Assignment(courseId, TargetType.DEPARTMENT, dueAt, assignedBy);
        a.departmentId = departmentId;
        return a;
    }

    static Assignment toMember(UUID courseId, UUID membershipId, Instant dueAt, UUID assignedBy) {
        Assignment a = new Assignment(courseId, TargetType.MEMBER, dueAt, assignedBy);
        a.membershipId = membershipId;
        return a;
    }

    private Assignment(UUID courseId, TargetType targetType, Instant dueAt, UUID assignedBy) {
        this.courseId = courseId;
        this.targetType = targetType;
        this.dueAt = dueAt;
        this.assignedBy = assignedBy;
        this.createdAt = Instant.now();
    }

    UUID getCourseId() {
        return courseId;
    }

    TargetType getTargetType() {
        return targetType;
    }

    UUID getDepartmentId() {
        return departmentId;
    }

    UUID getMembershipId() {
        return membershipId;
    }

    Instant getDueAt() {
        return dueAt;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}

/** One person taking one course. Created by assignment fan-out (native upsert), never by JPA. */
@Entity
@Table(name = "enrollment")
class Enrollment extends AbstractEntity {

    enum Status { NOT_STARTED, IN_PROGRESS, COMPLETED }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "membership_id", nullable = false, updatable = false)
    private UUID membershipId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Enrollment() {
    }

    void start(Instant now) {
        if (status == Status.NOT_STARTED) {
            status = Status.IN_PROGRESS;
            startedAt = now;
        }
    }

    /** Computed, never stored: a stored flag would need a midnight batch and drift when due dates change. */
    boolean isOverdue(Instant now) {
        return status != Status.COMPLETED && dueAt != null && dueAt.isBefore(now);
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getMembershipId() {
        return membershipId;
    }

    UUID getCourseId() {
        return courseId;
    }

    Status getStatus() {
        return status;
    }

    Instant getDueAt() {
        return dueAt;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}

/** Current state of one lesson for one enrollment (overwritten; history is in learning_record). */
@Entity
@Table(name = "lesson_progress")
class LessonProgress extends AbstractEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private UUID lessonId;

    @Column(name = "watched_sec", nullable = false)
    private int watchedSec;

    @Column(name = "last_position_sec", nullable = false)
    private int lastPositionSec;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "progressed_step", nullable = false)
    private int progressedStep;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected LessonProgress() {
    }

    /** A new playback session starts counting from now (time spent away is not watch time). */
    void launched(Instant now) {
        this.lastHeartbeatAt = now;
    }

    void apply(ProgressRules.Outcome outcome, Instant now) {
        this.watchedSec = outcome.watchedSec();
        this.lastPositionSec = outcome.positionSec();
        this.lastHeartbeatAt = now;
        this.progressedStep = Math.max(progressedStep, outcome.step());
    }

    /** @return true if this call completed it (false if it was already complete) */
    boolean complete(Instant now) {
        if (completedAt != null) {
            return false;
        }
        completedAt = now;
        return true;
    }

    UUID getEnrollmentId() {
        return enrollmentId;
    }

    UUID getLessonId() {
        return lessonId;
    }

    int getWatchedSec() {
        return watchedSec;
    }

    int getLastPositionSec() {
        return lastPositionSec;
    }

    Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    int getProgressedStep() {
        return progressedStep;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    boolean isCompleted() {
        return completedAt != null;
    }
}

@Entity
@Table(name = "quiz_attempt")
class QuizAttempt extends AbstractEntity {

    enum Status { IN_PROGRESS, SUBMITTED }

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(name = "quiz_id", nullable = false, updatable = false)
    private UUID quizId;

    @Column(name = "quiz_revision", nullable = false, updatable = false)
    private int quizRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, insertable = true, updatable = false)
    private Status status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", insertable = false, updatable = false)
    private String answers;

    @Column(insertable = false, updatable = false)
    private Integer score;

    @Column(insertable = false, updatable = false)
    private Boolean passed;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", updatable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at", insertable = false, updatable = false)
    private Instant submittedAt;

    protected QuizAttempt() {
    }

    /** Submission is a conditional UPDATE (see repository), so the entity never writes those columns. */
    QuizAttempt(UUID enrollmentId, UUID quizId, int quizRevision, Instant startedAt, Instant deadlineAt) {
        this.enrollmentId = enrollmentId;
        this.quizId = quizId;
        this.quizRevision = quizRevision;
        this.status = Status.IN_PROGRESS;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
    }

    UUID getEnrollmentId() {
        return enrollmentId;
    }

    UUID getQuizId() {
        return quizId;
    }

    int getQuizRevision() {
        return quizRevision;
    }

    Status getStatus() {
        return status;
    }

    String getAnswers() {
        return answers;
    }

    Integer getScore() {
        return score;
    }

    Boolean getPassed() {
        return passed;
    }

    Instant getStartedAt() {
        return startedAt;
    }

    Instant getDeadlineAt() {
        return deadlineAt;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }
}

@Entity
@Table(name = "certificate")
class Certificate extends AbstractEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private UUID enrollmentId;

    @Column(nullable = false, updatable = false, length = 32)
    private String serial;

    @Column(name = "object_key", length = 512)
    private String objectKey;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    protected Certificate() {
    }

    Certificate(UUID enrollmentId, String serial, Instant issuedAt) {
        this.enrollmentId = enrollmentId;
        this.serial = serial;
        this.issuedAt = issuedAt;
    }

    /** The PDF exists (rendered by the worker after the completion committed). */
    void attachPdf(String objectKey) {
        this.objectKey = objectKey;
    }

    String getObjectKey() {
        return objectKey;
    }

    UUID getEnrollmentId() {
        return enrollmentId;
    }

    String getSerial() {
        return serial;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }
}
