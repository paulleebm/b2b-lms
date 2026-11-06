package com.lms.tenancy.internal;

import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import com.lms.shared.web.ApiException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Company signup: a Keycloak organization + our tenant + the founder's ORG_ADMIN membership.
 *
 * <p>Keycloak and Postgres cannot share one transaction. So, saga-style:
 * <ol>
 *   <li>do the easily undone remote step first (create the organization, add the founder);</li>
 *   <li>then the local transaction (tenant + membership);</li>
 *   <li>if the local step fails, <b>compensate</b> by deleting the organization.</li>
 * </ol>
 * If the compensation itself fails we log it; a daily reconciliation job (Day 4) removes
 * Keycloak organizations that have no tenant.
 */
@Service
class TenantRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(TenantRegistrationService.class);

    /** Kept free for future subdomain routing and to avoid confusing names. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "api", "app", "auth", "cdn", "www", "mail", "static", "status", "support", "help", "lms");

    private final KeycloakAdminClient keycloak;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final TransactionTemplate tx;

    TenantRegistrationService(KeycloakAdminClient keycloak, TenantRepository tenants,
                              MembershipRepository memberships, PlatformTransactionManager txManager) {
        this.keycloak = keycloak;
        this.tenants = tenants;
        this.memberships = memberships;
        this.tx = new TransactionTemplate(txManager);
    }

    record Registered(UUID id, String slug, String name) {
    }

    Registered register(String name, String slug, String domain, UUID founderUserId, String founderKeycloakSub) {
        if (RESERVED.contains(slug)) {
            throw ApiException.badRequest("TENANT_SLUG_RESERVED", "사용할 수 없는 회사 코드입니다.");
        }
        if (tenants.existsBySlug(slug)) {
            throw ApiException.conflict("TENANT_SLUG_TAKEN", "이미 사용 중인 회사 코드입니다.");
        }

        String organizationId = keycloak.createOrganization(name, slug, domain);
        try {
            keycloak.addMember(organizationId, founderKeycloakSub);

            // Pick the id first and enter that tenant's context BEFORE the transaction starts:
            // the connection is bound to the tenant when the transaction acquires it.
            UUID tenantId = UuidV7.generate();
            TenantContext.runAs(tenantId, () -> tx.executeWithoutResult(status -> {
                tenants.save(new Tenant(tenantId, name, slug, organizationId));
                memberships.save(Membership.founder(founderUserId)); // tenant_id stamped by @TenantId
            }));
            return new Registered(tenantId, slug, name);
        } catch (RuntimeException e) {
            compensate(organizationId);
            if (e instanceof DataIntegrityViolationException) {
                throw ApiException.conflict("TENANT_SLUG_TAKEN", "이미 사용 중인 회사 코드입니다.");
            }
            throw e;
        }
    }

    private void compensate(String organizationId) {
        try {
            keycloak.deleteOrganization(organizationId);
        } catch (RuntimeException ex) {
            log.error("Compensation failed: Keycloak organization {} has no tenant", organizationId, ex);
        }
    }
}
