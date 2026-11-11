package com.lms.tenancy.internal;

import com.lms.shared.web.ApiNullable;
import com.lms.tenancy.Role;
import java.time.Instant;
import java.util.UUID;

/** Read model for the members list (JPQL constructor projection; no entity escapes the module). */
record MemberView(
        UUID id,
        UUID userId,
        String email,
        @ApiNullable String name,
        Role role,
        Membership.Status status,
        @ApiNullable UUID departmentId,
        @ApiNullable Instant invitedAt,
        @ApiNullable Instant joinedAt) {
}
