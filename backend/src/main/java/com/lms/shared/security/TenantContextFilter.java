package com.lms.shared.security;

import com.lms.shared.TenantContext;
import com.lms.shared.web.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Runs right after Spring Security has validated the bearer token.
 *
 * <ol>
 *   <li>Asks {@link TenantResolver} for the user, the tenant (from the token's organization) and
 *       the role (from the <b>current</b> membership in our DB — not from the token, so a disabled
 *       member is blocked immediately even though their token is still valid).</li>
 *   <li>Adds authorities {@code TENANT_MEMBER} and {@code ROLE_<role>} for URL rules and
 *       {@code @PreAuthorize}.</li>
 *   <li>Binds {@link TenantContext} for Hibernate {@code @TenantId} and RLS, and always clears it.</li>
 * </ol>
 */
public class TenantContextFilter extends OncePerRequestFilter {

    public static final String TENANT_MEMBER = "TENANT_MEMBER";

    private final TenantResolver tenantResolver;
    private final HandlerExceptionResolver exceptionResolver;

    public TenantContextFilter(TenantResolver tenantResolver, HandlerExceptionResolver exceptionResolver) {
        this.tenantResolver = tenantResolver;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth)) {
            chain.doFilter(request, response);
            return;
        }

        TenantResolver.ResolvedPrincipal principal;
        try {
            principal = tenantResolver.resolve(auth.getToken());
        } catch (ApiException e) {
            exceptionResolver.resolveException(request, response, null, e);
            return;
        }

        List<GrantedAuthority> authorities = new ArrayList<>(auth.getAuthorities());
        if (principal.tenantId() != null) {
            authorities.add(new SimpleGrantedAuthority(TENANT_MEMBER));
            authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role()));
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(auth.getToken(), authorities, auth.getName()));
        SecurityContextHolder.setContext(context);

        TenantContext.set(principal.tenantId(), principal.userId());
        if (principal.tenantId() != null) {
            MDC.put("tenantId", principal.tenantId().toString());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }
}
