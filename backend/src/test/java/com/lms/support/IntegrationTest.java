package com.lms.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for API integration tests: full Spring context + MockMvc + real Postgres.
 *
 * <p>Tokens: instead of a live Keycloak, tests attach an already-validated JWT with
 * Spring Security's {@code jwt()} post-processor. Signature checking is Spring Security's job
 * (and its own tests'); what we must prove is what <i>our</i> code does with the claims.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresContainer.register(registry);
        S3MockContainer.register(registry);
    }

    /** A user who is not acting for any company yet (no organization claim). */
    protected static JwtRequestPostProcessor user(String sub) {
        return jwt().jwt(j -> j.subject(sub).claim("email", sub + "@example.com").claim("name", sub));
    }

    /** A user acting for the company whose Keycloak organization alias is {@code orgAlias}. */
    protected static JwtRequestPostProcessor member(String sub, String orgAlias) {
        return jwt().jwt(j -> j.subject(sub)
                .claim("email", sub + "@example.com")
                .claim("name", sub)
                .claim("organization", List.of(orgAlias)));
    }

    /** Keycloak's alternative claim shape: {"acme": {"id": "..."}}. */
    protected static JwtRequestPostProcessor memberMapClaim(String sub, String orgAlias) {
        return jwt().jwt(j -> j.subject(sub)
                .claim("email", sub + "@example.com")
                .claim("organization", Map.of(orgAlias, Map.of("id", "kc-" + orgAlias))));
    }
}
