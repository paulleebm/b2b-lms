package com.lms.catalog;

import java.util.UUID;

/**
 * "Is anyone taking this course?" The catalog needs the answer (a course people are enrolled in
 * cannot go back to draft), but enrollments belong to the learning module, which already depends
 * on the catalog. So the catalog declares the question and learning implements it: the
 * dependency points one way only (dependency inversion), and Modulith verifies there is no cycle.
 */
public interface CourseUsage {

    boolean hasEnrollments(UUID courseId);
}
