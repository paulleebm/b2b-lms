package com.lms.catalog;

import java.util.UUID;

/** A course was published, unpublished or archived (audit log). {@code status}: PUBLISHED, DRAFT, ARCHIVED. */
public record CourseStatusChanged(UUID tenantId, UUID courseId, String title, String status) {
}
