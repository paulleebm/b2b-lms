package com.lms.shared.persistence;

import com.lms.shared.TenantContext;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Tells Hibernate which tenant the current session belongs to. Entities with a {@code @TenantId}
 * field are then filtered by it on reads and stamped with it on inserts — automatically.
 *
 * <p>Without a tenant we return {@link #NO_TENANT} instead of throwing, because global tables
 * (tenant, app_user) are read before the tenant is known (e.g. while resolving it from the token).
 * NO_TENANT matches no row, and Postgres RLS rejects any write stamped with it: fail-closed.
 */
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    public static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.tenantId().orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
