package com.lms.tenancy.internal;

import com.lms.tenancy.internal.TenancyController.CurrentTenant;
import com.lms.tenancy.internal.TenancyController.MeResponse;
import com.lms.tenancy.internal.TenancyController.MembershipItem;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MeQuery {

    private final AppUserRepository users;
    private final MembershipRepository memberships;

    MeQuery(AppUserRepository users, MembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    MeResponse load(UUID userId, UUID currentTenantId) {
        AppUser user = users.findById(userId).orElseThrow();
        List<MembershipItem> items = memberships.findAllSummariesForUser(userId).stream()
                .map(r -> new MembershipItem(r.getTenantId(), r.getSlug(), r.getName(), r.getRole(), r.getStatus()))
                .toList();

        CurrentTenant current = null;
        String role = null;
        if (currentTenantId != null) {
            for (MembershipItem item : items) {
                if (item.tenantId().equals(currentTenantId)) {
                    current = new CurrentTenant(item.tenantId(), item.slug(), item.name());
                    role = item.role();
                }
            }
        }
        return new MeResponse(user.getId(), user.getEmail(), user.getName(), current, role, items);
    }
}
