package com.lms.tenancy.internal;

import com.lms.tenancy.Role;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}

interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByKeycloakSub(String keycloakSub);
}

interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /** No tenant condition here on purpose: Hibernate @TenantId and RLS add it. */
    Optional<Membership> findByUserId(UUID userId);

    /** Members of the current company with their profile, for the admin "멤버" page. */
    @Query("""
            select new com.lms.tenancy.internal.MemberView(
                m.id, m.userId, u.email, u.name, m.role, m.status, m.departmentId, m.invitedAt, m.joinedAt)
            from Membership m join AppUser u on u.id = m.userId
            order by m.status, u.name, u.email
            """)
    List<MemberView> findAllViews();

    /**
     * Locks the company's active admins so two concurrent demotions cannot both pass the
     * "at least one admin remains" check (classic check-then-act race).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membership m where m.role = :role and m.status = 'ACTIVE' order by m.id")
    List<Membership> lockActiveByRole(@Param("role") Role role);

    /** First sign-in after an invitation. Conditional: two racing first requests activate once. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Membership m set m.status = 'ACTIVE', m.joinedAt = :now where m.id = :id and m.status = 'INVITED'")
    int activateIfInvited(@Param("id") UUID id, @Param("now") Instant now);

    /** Display names for other modules (course owner, ...). RLS keeps it to the current company. */
    @Query("select m.id as id, coalesce(u.name, u.email) as name from Membership m join AppUser u on u.id = m.userId where m.id in :ids")
    List<DisplayNameRow> findDisplayNames(@Param("ids") java.util.Collection<UUID> ids);

    interface DisplayNameRow {
        UUID getId();

        String getName();
    }

    /** Cross-tenant list via the SECURITY DEFINER function (see V1__tenancy.sql). */
    @Query(value = """
            select m.tenant_id as "tenantId", m.slug as "slug", m.name as "name",
                   m.role as "role", m.status as "status"
            from user_memberships(:userId) m
            """, nativeQuery = true)
    List<MembershipSummaryRow> findAllSummariesForUser(@Param("userId") UUID userId);

    interface MembershipSummaryRow {
        UUID getTenantId();

        String getSlug();

        String getName();

        String getRole();

        String getStatus();
    }
}
