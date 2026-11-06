package com.lms.tenancy.internal;

import com.lms.shared.persistence.AbstractEntity;
import com.lms.shared.web.ApiException;
import com.lms.tenancy.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * "This person, in this company, with this role." Protected by Hibernate @TenantId and RLS.
 *
 * <p>Lifecycle: INVITED (admin invited, person has not signed in yet) → ACTIVE (first sign-in
 * with this company's organization in the token) → DISABLED (left the company; history kept).
 * State changes live here so every caller goes through the same rules.
 */
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

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "invited_at")
    private Instant invitedAt;

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

    static Membership invited(UUID userId, Role role, UUID departmentId, UUID invitedBy) {
        Membership m = new Membership();
        m.userId = userId;
        m.role = role;
        m.status = Status.INVITED;
        m.departmentId = departmentId;
        m.invitedBy = invitedBy;
        m.invitedAt = Instant.now();
        m.createdAt = m.invitedAt;
        return m;
    }

    /** Re-invite someone who left: keeps the row (and their training history), resets the state. */
    void reinvite(Role role, UUID departmentId, UUID invitedBy) {
        if (status != Status.DISABLED) {
            throw new IllegalStateException("Only a disabled membership can be re-invited");
        }
        this.role = role;
        this.departmentId = departmentId;
        this.invitedBy = invitedBy;
        this.invitedAt = Instant.now();
        this.status = Status.INVITED;
    }

    void changeRole(Role role) {
        this.role = role;
    }

    void assignDepartment(UUID departmentId) {
        this.departmentId = departmentId;
    }

    void disable() {
        if (status == Status.INVITED) {
            throw ApiException.conflict("MEMBER_NOT_JOINED", "아직 가입하지 않은 멤버는 비활성화 대신 초대를 취소하세요.");
        }
        this.status = Status.DISABLED;
    }

    void enable() {
        if (status != Status.DISABLED) {
            throw ApiException.conflict("MEMBER_NOT_DISABLED", "비활성 상태인 멤버만 다시 활성화할 수 있습니다.");
        }
        this.status = Status.ACTIVE;
    }

    UUID getTenantId() {
        return tenantId;
    }

    UUID getUserId() {
        return userId;
    }

    Role getRole() {
        return role;
    }

    Status getStatus() {
        return status;
    }

    UUID getDepartmentId() {
        return departmentId;
    }

    Instant getInvitedAt() {
        return invitedAt;
    }

    Instant getJoinedAt() {
        return joinedAt;
    }
}
