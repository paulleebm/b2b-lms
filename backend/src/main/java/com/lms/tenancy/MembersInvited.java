package com.lms.tenancy;

import java.util.UUID;

/** People were invited (audit log). */
public record MembersInvited(UUID tenantId, int count, String role) {
}
