package com.lms.tenancy.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
