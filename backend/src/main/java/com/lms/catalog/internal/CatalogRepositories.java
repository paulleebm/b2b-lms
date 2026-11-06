package com.lms.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** No tenant condition in any query: Hibernate @TenantId and RLS add it. */
interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findAllByOrderByUpdatedAtDesc();

    List<Course> findAllByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    /** Section and lesson counts for the course list, in one query. */
    @Query("""
            select s.courseId as courseId, count(distinct s.id) as sections, count(l.id) as lessons
            from CourseSection s left join Lesson l on l.sectionId = s.id
            where s.courseId in :courseIds
            group by s.courseId
            """)
    List<OutlineCount> countOutlines(@Param("courseIds") Collection<UUID> courseIds);

    interface OutlineCount {
        UUID getCourseId();

        long getSections();

        long getLessons();
    }
}

interface CourseSectionRepository extends JpaRepository<CourseSection, UUID> {

    List<CourseSection> findAllByCourseIdOrderByPosition(UUID courseId);
}

interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findAllByCourseIdOrderByPosition(UUID courseId);

    List<Lesson> findAllBySectionIdOrderByPosition(UUID sectionId);
}
