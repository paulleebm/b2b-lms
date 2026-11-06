package com.lms.tenancy.internal;

import com.lms.shared.persistence.AbstractEntity;
import com.lms.tenancy.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** "This person, in this company, with this role." Protected by Hibernate @TenantId and RLS. */
@Entity
@Table(name = "membership")
class Membership extends AbstractEntity {

    enum Status { INVITED, ACTIVE, DISABLED }

    /** Filled in by Hibernate from the current tenant; never set by application code. */
    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Membership() {
    }

    static Membership founder(UUID userId) {
        Membership m = new Membership();
        m.userId = userId;
        m.role = Role.ORG_ADMIN;
        m.status = Status.ACTIVE;
        m.joinedAt = Instant.now();
        m.createdAt = m.joinedAt;
        return m;
    }

    UUID getTenantId() {
        return tenantId;
    }

    Role getRole() {
        return role;
    }

    Status getStatus() {
        return status;
    }
}
