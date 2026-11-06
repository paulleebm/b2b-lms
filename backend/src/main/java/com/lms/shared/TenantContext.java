package com.lms.shared;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Which company and which person is this request for?" — set once per request by the security
 * layer, read by Hibernate ({@code @TenantId}), by the DataSource (Postgres RLS) and by logs.
 *
 * <p>ThreadLocal works with virtual threads: each request runs on its own (virtual) thread.
 * The value is always cleared in a {@code finally} block so a pooled/reused thread never
 * carries the previous request's tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> MEMBERSHIP = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> tenantId() {
        return Optional.ofNullable(TENANT.get());
    }

    /** Fail-closed accessor for code that must never run without a tenant. */
    public static UUID requireTenantId() {
        UUID id = TENANT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant in context");
        }
        return id;
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(USER.get());
    }

    /** The caller's membership in the current tenant (who owns a course, who invited whom). */
    public static Optional<UUID> membershipId() {
        return Optional.ofNullable(MEMBERSHIP.get());
    }

    public static UUID requireMembershipId() {
        UUID id = MEMBERSHIP.get();
        if (id == null) {
            throw new IllegalStateException("No membership in context");
        }
        return id;
    }

    public static void set(UUID tenantId, UUID userId) {
        set(tenantId, userId, null);
    }

    public static void set(UUID tenantId, UUID userId, UUID membershipId) {
        TENANT.set(tenantId);
        USER.set(userId);
        MEMBERSHIP.set(membershipId);
    }

    public static void clear() {
        TENANT.remove();
        USER.remove();
        MEMBERSHIP.remove();
    }

    /**
     * Runs {@code work} as the given tenant and restores the previous context afterwards.
     * Open the transaction INSIDE {@code work}: the tenant is bound to the DB connection
     * when the transaction acquires it.
     */
    public static <T> T callAs(UUID tenantId, Supplier<T> work) {
        UUID previousTenant = TENANT.get();
        UUID previousUser = USER.get();
        TENANT.set(tenantId);
        try {
            return work.get();
        } finally {
            TENANT.set(previousTenant);
            USER.set(previousUser);
            if (previousTenant == null) {
                TENANT.remove();
            }
            if (previousUser == null) {
                USER.remove();
            }
        }
    }

    public static void runAs(UUID tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
