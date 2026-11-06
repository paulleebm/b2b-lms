package com.lms.tenancy;

import java.util.UUID;

/**
 * An ACTIVE member moved to another department (or left one: {@code departmentId} null).
 * The learning module adds the new department's assignments; existing enrollments stay.
 */
public record MemberDepartmentChanged(UUID tenantId, UUID membershipId, UUID departmentId) {
}
