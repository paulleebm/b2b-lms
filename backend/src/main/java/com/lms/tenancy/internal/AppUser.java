package com.lms.tenancy.internal;

import com.lms.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A person, global across companies. Credentials live in Keycloak; we keep only the link
 * ({@code keycloakSub}) and display data, refreshed from the token on each login.
 * Never queried on its own for other people — always through a (RLS-protected) membership.
 */
@Entity
@Table(name = "app_user")
class AppUser extends AbstractEntity {

    @Column(name = "keycloak_sub", nullable = false, unique = true, length = 64)
    private String keycloakSub;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    AppUser(String keycloakSub, String email, String name) {
        this.keycloakSub = keycloakSub;
        this.email = email;
        this.name = name;
        this.createdAt = Instant.now();
    }

    /** @return true if anything changed (so the caller knows whether a write is needed) */
    boolean refreshProfile(String email, String name) {
        boolean changed = !Objects.equals(this.email, email) || !Objects.equals(this.name, name);
        this.email = email;
        this.name = name;
        return changed;
    }

    String getKeycloakSub() {
        return keycloakSub;
    }

    String getEmail() {
        return email;
    }

    String getName() {
        return name;
    }
}
