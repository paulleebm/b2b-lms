package com.lms.tenancy.internal;

import com.lms.shared.TenantContext;
import com.lms.shared.security.TenantResolver;
import com.lms.shared.web.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Token → (user, tenant, role), for every authenticated request.
 *
 * <ol>
 *   <li><b>User</b>: find by Keycloak {@code sub}; create on first sight (just-in-time provisioning).</li>
 *   <li><b>Tenant</b>: the organization alias in the token = our tenant slug.</li>
 *   <li><b>Role</b>: the membership <i>as it is now in our DB</i>, looked up inside that tenant's
 *       context (so Hibernate and RLS both scope the query). No ACTIVE membership → 403.</li>
 * </ol>
 * Day 2 adds a 60-second Redis cache for step 3 (evicted on role/status change).
 */
@Component
class MembershipTenantResolver implements TenantResolver {

    private final AppUserRepository users;
    private final TenantRepository tenants;
    private final MembershipRepository memberships;
    private final TransactionTemplate tx;

    MembershipTenantResolver(AppUserRepository users, TenantRepository tenants,
                             MembershipRepository memberships, PlatformTransactionManager txManager) {
        this.users = users;
        this.tenants = tenants;
        this.memberships = memberships;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public ResolvedPrincipal resolve(Jwt jwt) {
        AppUser user = provision(jwt);

        Optional<String> alias = OrganizationClaim.selectedAlias(jwt);
        if (alias.isEmpty()) {
            return new ResolvedPrincipal(user.getId(), null, null);
        }

        Tenant tenant = tenants.findBySlug(alias.get())
                .orElseThrow(() -> ApiException.forbidden("TENANT_NOT_REGISTERED", "등록되지 않은 회사입니다."));

        Membership membership = TenantContext.callAs(tenant.getId(),
                () -> tx.execute(status -> memberships.findByUserId(user.getId()).orElse(null)));

        if (membership == null || membership.getStatus() != Membership.Status.ACTIVE) {
            throw ApiException.forbidden("NOT_A_MEMBER", "이 회사의 활성 멤버가 아닙니다.");
        }
        return new ResolvedPrincipal(user.getId(), tenant.getId(), membership.getRole().name());
    }

    /** Just-in-time user creation, safe against two first requests racing each other. */
    private AppUser provision(Jwt jwt) {
        String sub = jwt.getSubject();
        String email = Optional.ofNullable(jwt.getClaimAsString("email")).orElse(sub);
        String name = jwt.getClaimAsString("name");
        try {
            return tx.execute(status -> {
                AppUser user = users.findByKeycloakSub(sub).orElse(null);
                if (user == null) {
                    return users.save(new AppUser(sub, email, name));
                }
                if (user.refreshProfile(email, name)) {
                    return users.save(user);
                }
                return user;
            });
        } catch (DataIntegrityViolationException raceLost) {
            // The other request inserted the same sub first; the unique constraint kept us consistent.
            return users.findByKeycloakSub(sub).orElseThrow(() -> raceLost);
        }
    }

    static UUID requireUserId() {
        return TenantContext.userId().orElseThrow(() -> new IllegalStateException("No user in context"));
    }
}
