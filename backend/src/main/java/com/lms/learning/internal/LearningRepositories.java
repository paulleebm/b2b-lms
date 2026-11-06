package com.lms.learning.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No tenant condition in any query: Hibernate @TenantId and RLS add it. */
interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findAllByOrderByCreatedAtDesc();

    /** What applies to this person: their department's assignments and their own. */
    @Query("""
            select a from Assignment a
            where (a.targetType = com.lms.learning.internal.Assignment.TargetType.DEPARTMENT and a.departmentId = :departmentId)
               or (a.targetType = com.lms.learning.internal.Assignment.TargetType.MEMBER and a.membershipId = :membershipId)
            """)
    List<Assignment> findApplicable(@Param("departmentId") UUID departmentId, @Param("membershipId") UUID membershipId);
}

interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findByIdAndMembershipId(UUID id, UUID membershipId);

    List<Enrollment> findAllByMembershipId(UUID membershipId);

    boolean existsByCourseId(UUID courseId);

    @Query("""
            select e.courseId as courseId, count(e) as enrolled,
                   sum(case when e.status = com.lms.learning.internal.Enrollment.Status.COMPLETED then 1 else 0 end) as completed
            from Enrollment e where e.courseId in :courseIds group by e.courseId
            """)
    List<CourseCount> countByCourse(@Param("courseIds") Collection<UUID> courseIds);

    interface CourseCount {
        UUID getCourseId();

        long getEnrolled();

        long getCompleted();
    }

    /** Exactly once, even if the last lesson and a passing quiz race each other. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Enrollment e set e.status = com.lms.learning.internal.Enrollment.Status.COMPLETED, e.completedAt = :now
            where e.id = :id and e.status <> com.lms.learning.internal.Enrollment.Status.COMPLETED
            """)
    int completeIfNot(@Param("id") UUID id, @Param("now") Instant now);
}

interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    List<LessonProgress> findAllByEnrollmentId(UUID enrollmentId);

    List<LessonProgress> findAllByEnrollmentIdIn(Collection<UUID> enrollmentIds);

    /** Serializes heartbeats of one lesson so two in flight cannot both add their credit. */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from LessonProgress p where p.enrollmentId = :enrollmentId and p.lessonId = :lessonId")
    Optional<LessonProgress> lock(@Param("enrollmentId") UUID enrollmentId, @Param("lessonId") UUID lessonId);
}

interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findAllByEnrollmentIdOrderByStartedAt(UUID enrollmentId);

    Optional<QuizAttempt> findByEnrollmentIdAndStatus(UUID enrollmentId, QuizAttempt.Status status);

    long countByEnrollmentIdAndStatus(UUID enrollmentId, QuizAttempt.Status status);

    /** 0 rows = already submitted (double click, retry): the first submission is the one that counts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            update quiz_attempt set status = 'SUBMITTED', answers = cast(:answers as jsonb), score = :score,
                   passed = :passed, submitted_at = :now
            where id = :id and status = 'IN_PROGRESS'
            """, nativeQuery = true)
    int submitIfOpen(@Param("id") UUID id, @Param("answers") String answers, @Param("score") int score,
                     @Param("passed") boolean passed, @Param("now") Instant now);
}

interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    Optional<Certificate> findByEnrollmentId(UUID enrollmentId);

    List<Certificate> findAllByEnrollmentIdIn(Collection<UUID> enrollmentIds);

    @Query("select c from Certificate c where c.objectKey is null and c.issuedAt < :before")
    List<Certificate> findMissingPdf(@Param("before") Instant before);
}
