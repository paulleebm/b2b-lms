package com.lms.tenancy.internal;

import com.lms.shared.web.ApiNullable;
import com.lms.shared.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TenancyController {

    private final TenantRegistrationService registration;
    private final MeQuery meQuery;

    TenancyController(TenantRegistrationService registration, MeQuery meQuery) {
        this.registration = registration;
        this.meQuery = meQuery;
    }

    /** Who am I, which company am I acting for, and which companies can I switch to. */
    @GetMapping("/v1/me")
    MeResponse me() {
        return meQuery.load(MembershipTenantResolver.requireUserId(), TenantContext.tenantId().orElse(null));
    }

    /** Company signup. The caller becomes its first ORG_ADMIN. */
    @PostMapping("/v1/tenants")
    ResponseEntity<TenantResponse> register(@Valid @RequestBody RegisterTenantRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        var r = registration.register(
                request.name().strip(), request.slug(), request.domain().toLowerCase(),
                MembershipTenantResolver.requireUserId(), jwt.getSubject());
        return ResponseEntity.created(URI.create("/v1/tenants/" + r.id()))
                .body(new TenantResponse(r.id(), r.slug(), r.name()));
    }

    record RegisterTenantRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,28}[a-z0-9]$",
                    message = "소문자, 숫자, 하이픈 3~30자") String slug,
            @NotBlank @Size(max = 253) @Pattern(regexp = "^(?=.{1,253}$)([a-zA-Z0-9-]{1,63}\\.)+[a-zA-Z]{2,63}$",
                    message = "예: acme.com") String domain) {
    }

    record TenantResponse(UUID id, String slug, String name) {
    }

    record MeResponse(UUID userId, String email, @ApiNullable String name, @ApiNullable CurrentTenant currentTenant,
                      @ApiNullable String role,
                      List<MembershipItem> memberships) {
    }

    record CurrentTenant(UUID id, String slug, String name) {
    }

    record MembershipItem(UUID tenantId, String slug, String name, String role, String status) {
    }
}
