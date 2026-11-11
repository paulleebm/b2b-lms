package com.lms.tenancy.internal;

import com.lms.tenancy.Companies;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompaniesService implements Companies {

    private final TenantRepository tenants;

    CompaniesService(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> name(UUID tenantId) {
        return tenants.findById(tenantId).map(Tenant::getName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> allIds() {
        return tenants.findAll().stream().map(Tenant::getId).toList();
    }
}
