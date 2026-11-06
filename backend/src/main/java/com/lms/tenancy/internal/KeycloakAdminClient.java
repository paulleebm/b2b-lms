package com.lms.tenancy.internal;

import com.lms.shared.web.ApiException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Calls the Keycloak Admin REST API as the {@code lms-admin} service account
 * (OAuth2 client credentials). Used to keep Keycloak organizations in step with our tenants.
 *
 * <p>Plain RestClient instead of the official admin client library: the library pins its own
 * HTTP/JSON stack, which tends to conflict with Spring's; we only need four endpoints.
 */
@Component
public class KeycloakAdminClient {

    private final KeycloakProperties props;
    private final RestClient http;
    private volatile CachedToken token;

    KeycloakAdminClient(KeycloakProperties props, RestClient.Builder builder) {
        this.props = props;
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    /** @return the new organization's id */
    public String createOrganization(String name, String alias, String domain) {
        try {
            URI location = http.post()
                    .uri("/admin/realms/{realm}/organizations", props.realm())
                    .headers(h -> h.setBearerAuth(accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "name", name,
                            "alias", alias,
                            "enabled", true,
                            "domains", List.of(Map.of("name", domain))))
                    .retrieve()
                    .toBodilessEntity()
                    .getHeaders()
                    .getLocation();
            if (location == null) {
                throw new IllegalStateException("Keycloak did not return the organization location");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.conflict("ORGANIZATION_CONFLICT", "이미 등록된 회사 이름, 코드 또는 도메인입니다.");
        }
    }

    public void addMember(String organizationId, String keycloakUserId) {
        http.post()
                .uri("/admin/realms/{realm}/organizations/{id}/members", props.realm(), organizationId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("\"" + keycloakUserId + "\"")   // the endpoint takes the user id as a bare JSON string
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteOrganization(String organizationId) {
        http.delete()
                .uri("/admin/realms/{realm}/organizations/{id}", props.realm(), organizationId)
                .headers(h -> h.setBearerAuth(accessToken()))
                .retrieve()
                .toBodilessEntity();
    }

    private String accessToken() {
        CachedToken current = token;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return current.value();
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.adminClientId());
        form.add("client_secret", props.adminClientSecret());
        TokenResponse response = http.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", props.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.access_token() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "IDP_UNAVAILABLE", "인증 서버에 연결할 수 없습니다.");
        }
        token = new CachedToken(response.access_token(), Instant.now().plusSeconds(response.expires_in()));
        return token.value();
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    private record TokenResponse(String access_token, long expires_in) {
    }
}
