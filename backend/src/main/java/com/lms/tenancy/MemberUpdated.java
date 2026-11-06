package com.lms.tenancy;

import java.util.UUID;

/** An admin changed a member. {@code change}: ROLE, STATUS or DEPARTMENT (audit log). */
public record MemberUpdated(UUID tenantId, UUID membershipId, String change, Object from, Object to) {
}
