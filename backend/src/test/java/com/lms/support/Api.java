package com.lms.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.lms.tenancy.internal.KeycloakAdminClient;
import com.lms.tenancy.internal.KeycloakAdminClient.KeycloakUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/** Test fixture: companies and people set up through the real API, as a user would. */
public final class Api {

    private final MockMvc mvc;
    public final String run = UUID.randomUUID().toString().substring(0, 8);

    public Api(MockMvc mvc, KeycloakAdminClient keycloak) {
        this.mvc = mvc;
        given(keycloak.createOrganization(anyString(), anyString(), anyString()))
                .willAnswer(inv -> "kc-" + inv.getArgument(1));
        given(keycloak.ensureUser(anyString()))
                .willAnswer(inv -> new KeycloakUser(subFor(inv.getArgument(0)), true, true));
    }

    public static String subFor(String email) {
        return "kc-" + email.substring(0, email.indexOf('@')).toLowerCase();
    }

    public static JwtRequestPostProcessor as(String sub, String org) {
        return SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(sub)
                .claim("email", sub + "@example.com").claim("name", sub)
                .claim("organization", List.of(org)));
    }

    /** Registers a company; {@code adminSub} becomes its first ORG_ADMIN. Returns the slug. */
    public String company(String prefix, String adminSub) throws Exception {
        String slug = prefix + "-" + run;
        json(post("/v1/tenants").with(SecurityMockMvcRequestPostProcessors.jwt().jwt(j -> j.subject(adminSub)
                        .claim("email", adminSub + "@example.com").claim("name", adminSub)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + slug + "\",\"slug\":\"" + slug + "\",\"domain\":\"" + slug + ".test\"}"));
        return slug;
    }

    /** Invites someone with {@code role} and signs them in once, so they are ACTIVE. Returns their token subject. */
    public String joined(String adminSub, String org, String name, String role) throws Exception {
        String email = name + "-" + run + "@" + org + ".test";
        json(post("/v1/invitations").with(as(adminSub, org)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"" + email + "\"],\"role\":\"" + role + "\"}"));
        String sub = subFor(email);
        json(get("/v1/me").with(as(sub, org)));
        return sub;
    }

    public String json(RequestBuilder request) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(response.getStatus()).as(body).isBetween(200, 299);
        return body;
    }

    public MockHttpServletResponse raw(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    public static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    public static Map<String, Object> obj(String json, String path) {
        return JsonPath.read(json, path);
    }
}
