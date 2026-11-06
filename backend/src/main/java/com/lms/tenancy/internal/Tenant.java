package com.lms.tenancy.internal;

import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A customer company. Global table (no RLS): it is how a tenant is found in the first place. */
@Entity
@Table(name = "tenant")
class Tenant extends AbstractEntity {

    @Column(nullable = false, length = 100)
    private String name;

    /** Same value as the Keycloak organization alias (what the token's organization claim carries). */
    @Column(nullable = false, unique = true, length = 30)
    private String slug;

    @Column(name = "keycloak_org_id", nullable = false, unique = true, length = 64)
    private String keycloakOrgId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Tenant() {
    }

    Tenant(UUID id, String name, String slug, String keycloakOrgId) {
        super(id);
        this.name = name;
        this.slug = slug;
        this.keycloakOrgId = keycloakOrgId;
        this.createdAt = Instant.now();
    }

    String getName() {
        return name;
    }

    String getSlug() {
        return slug;
    }
}
