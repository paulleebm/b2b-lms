package com.lms.shared.security;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a validated token into "who is this, in which company, with which role".
 * Implemented by the tenancy module (it owns users, tenants and memberships); declared here so
 * the shared security layer does not depend on any business module.
 */
public interface TenantResolver {

    ResolvedPrincipal resolve(Jwt jwt);

    /**
     * @param tenantId     null when the token carries no organization (e.g. before company signup)
     * @param membershipId null when tenantId is null
     * @param role         null when tenantId is null
     */
    record ResolvedPrincipal(UUID userId, UUID tenantId, UUID membershipId, String role) {
    }
}
