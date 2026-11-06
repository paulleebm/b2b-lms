package com.lms.shared.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * The API is an OAuth2 <b>resource server</b>: it only validates Keycloak-issued JWTs
 * (signature via JWKS, issuer, audience {@code lms-api}, expiry — configured in application.yml).
 *
 * <ul>
 *   <li>Stateless: no HTTP session, no cookies → CSRF protection is not needed for this API
 *       (browsers never attach the Authorization header on their own).</li>
 *   <li>After the token is validated, {@link TenantContextFilter} turns "who + which organization"
 *       into a tenant and a role from <i>our</i> database (authorization lives in the app).</li>
 *   <li>401/403 raised inside the filter chain are rendered by the same RFC 9457 handler as
 *       controller errors, by delegating to Spring MVC's exception resolver.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http,
                            TenantResolver tenantResolver,
                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v1/public/**").permitAll()
                        // Allowed before the user belongs to any company:
                        .requestMatchers(HttpMethod.GET, "/v1/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/tenants").authenticated()
                        // Everything else needs an ACTIVE membership in the token's organization:
                        .anyRequest().hasAuthority(TenantContextFilter.TENANT_MEMBER))
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> {})
                        .authenticationEntryPoint((req, res, ex) -> resolver.resolveException(req, res, null, ex)))
                .exceptionHandling(e -> e
                        .accessDeniedHandler((req, res, ex) -> resolver.resolveException(req, res, null, ex)))
                .addFilterAfter(new TenantContextFilter(tenantResolver, resolver), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
