package com.lms.tenancy.internal;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads the Keycloak {@code organization} claim. Depending on mapper settings Keycloak emits either
 * a list of aliases ({@code ["acme"]}) or a map keyed by alias ({@code {"acme": {"id": "..."}}}).
 *
 * <p>The BFF requests one organization at a time ({@code scope=organization:<alias>}), so we only
 * accept a token that names exactly one. Zero → "not acting for a company yet". Several → ambiguous,
 * treated the same way so the client must pick one.
 */
final class OrganizationClaim {

    static final String CLAIM = "organization";

    private OrganizationClaim() {
    }

    static Optional<String> selectedAlias(Jwt jwt) {
        Object raw = jwt.getClaims().get(CLAIM);
        if (raw instanceof Map<?, ?> map && map.size() == 1) {
            return Optional.of(String.valueOf(map.keySet().iterator().next()));
        }
        if (raw instanceof Collection<?> list && list.size() == 1) {
            return Optional.of(String.valueOf(list.iterator().next()));
        }
        if (raw instanceof String single && !single.isBlank()) {
            return Optional.of(single);
        }
        return Optional.empty();
    }
}
