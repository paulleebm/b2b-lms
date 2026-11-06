package com.lms;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lms.support.IntegrationTest;
import com.lms.tenancy.internal.KeycloakAdminClient;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The core promise of a multi-tenant SaaS, proven end to end through the HTTP API:
 * one company can never see or touch another company's data.
 */
class TenantIsolationIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    /** Keycloak is an external system; its admin API is replaced by a mock in these tests. */
    @MockitoBean
    KeycloakAdminClient keycloak;

    String acme;     // unique per test so tests stay independent on the shared database
    String beta;
    String alice;    // ORG_ADMIN of acme
    String bob;      // ORG_ADMIN of beta

    @BeforeEach
    void twoCompanies() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        acme = "acme-" + run;
        beta = "beta-" + run;
        alice = "alice-" + run;
        bob = "bob-" + run;
        given(keycloak.createOrganization(anyString(), anyString(), anyString()))
                .willAnswer(inv -> "kc-" + inv.getArgument(1));

        registerCompany(alice, "ACME " + run, acme);
        registerCompany(bob, "Beta " + run, beta);
    }

    @Test
    @DisplayName("A회사 관리자가 만든 부서를 B회사 관리자는 조회할 수 없다 (404, 403 아님)")
    void otherTenantsResourceIsNotFound() throws Exception {
        String departmentId = createDepartment(alice, acme, "개발팀");

        mvc.perform(get("/v1/departments/{id}", departmentId).with(member(alice, acme)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("개발팀"));

        mvc.perform(get("/v1/departments/{id}", departmentId).with(member(bob, beta)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("목록에도 다른 회사의 데이터는 섞이지 않는다")
    void listsAreScopedToTheTenant() throws Exception {
        createDepartment(alice, acme, "개발팀");
        createDepartment(alice, acme, "영업팀");
        createDepartment(bob, beta, "인사팀");

        mvc.perform(get("/v1/departments").with(member(alice, acme)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", org.hamcrest.Matchers.containsInAnyOrder("개발팀", "영업팀")));

        mvc.perform(get("/v1/departments").with(member(bob, beta)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("인사팀"));
    }

    @Test
    @DisplayName("같은 이름의 부서는 회사마다 따로 만들 수 있다 (유니크 제약도 테넌트 단위)")
    void uniquenessIsPerTenant() throws Exception {
        createDepartment(alice, acme, "개발팀");
        createDepartment(bob, beta, "개발팀");

        mvc.perform(post("/v1/departments").with(member(alice, acme))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"개발팀\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NAME_TAKEN"));
    }

    @Test
    @DisplayName("소속되지 않은 회사의 조직 정보가 담긴 토큰은 403 NOT_A_MEMBER")
    void tokenForForeignOrganizationIsRejected() throws Exception {
        mvc.perform(get("/v1/departments").with(member(alice, beta)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));
    }

    @Test
    @DisplayName("조직 정보가 없는 토큰은 회사 데이터에 접근할 수 없지만 /v1/me 는 볼 수 있다")
    void noOrganizationMeansNoTenantAccess() throws Exception {
        mvc.perform(get("/v1/departments").with(user(alice)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/v1/me").with(user(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant").doesNotExist())
                .andExpect(jsonPath("$.memberships.length()").value(1))
                .andExpect(jsonPath("$.memberships[0].slug").value(acme))
                .andExpect(jsonPath("$.memberships[0].role").value("ORG_ADMIN"));
    }

    @Test
    @DisplayName("/v1/me 는 현재 회사와 역할을 알려주고, Keycloak의 map 형태 조직 클레임도 처리한다")
    void meShowsCurrentTenant() throws Exception {
        mvc.perform(get("/v1/me").with(memberMapClaim(alice, acme)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTenant.slug").value(acme))
                .andExpect(jsonPath("$.role").value("ORG_ADMIN"));
    }

    @Test
    @DisplayName("이미 사용 중인 회사 코드로는 등록할 수 없다")
    void duplicateSlugIsRejected() throws Exception {
        mvc.perform(post("/v1/tenants").with(user("carol-" + acme))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Copycat\",\"slug\":\"" + acme + "\",\"domain\":\"copycat.test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SLUG_TAKEN"));
    }

    @Test
    @DisplayName("입력 검증 실패는 RFC 9457 형식으로 필드 목록과 함께 400")
    void validationErrorsAreProblemDetails() throws Exception {
        mvc.perform(post("/v1/tenants").with(user(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"slug\":\"A!\",\"domain\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    @DisplayName("토큰이 없으면 401, 문제 형식으로")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"));
    }

    private void registerCompany(String sub, String name, String slug) throws Exception {
        mvc.perform(post("/v1/tenants").with(user(sub))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"domain\":\"" + slug + ".test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value(slug));
    }

    private String createDepartment(String sub, String org, String name) throws Exception {
        String body = mvc.perform(post("/v1/departments").with(member(sub, org))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id");
    }
}
