package com.lms.tenancy;

import java.util.UUID;

/**
 * Published when an invited person signs in for the first time and becomes an ACTIVE member.
 * Day 3: the learning module listens to create enrollments for the department's active
 * assignments (new-hire auto-assignment, docs/05 §7).
 */
public record MemberJoined(UUID tenantId, UUID membershipId, UUID departmentId) {
}
