package com.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lms.support.IntegrationTest;
import com.lms.tenancy.internal.KeycloakAdminClient;
import com.lms.tenancy.internal.KeycloakAdminClient.KeycloakUser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * Members and invitations, end to end: invite → first sign-in activates → admin changes role,
 * department and status, with the guards that keep a company manageable.
 */
class MemberManagementIT extends IntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    KeycloakAdminClient keycloak;

    String run;
    String acme;
    String beta;
    String alice;   // ORG_ADMIN of acme (founder)
    String bob;     // ORG_ADMIN of beta

    @BeforeEach
    void companies() throws Exception {
        run = UUID.randomUUID().toString().substring(0, 8);
        acme = "acme-" + run;
        beta = "beta-" + run;
        alice = "alice-" + run;
        bob = "bob-" + run;
        given(keycloak.createOrganization(anyString(), anyString(), anyString()))
                .willAnswer(inv -> "kc-" + inv.getArgument(1));
        // Keycloak user id = "kc-" + local part, created fresh, so a test can sign in as that person.
        given(keycloak.ensureUser(anyString()))
                .willAnswer(inv -> new KeycloakUser(subFor(inv.getArgument(0)), true, true));

        register(alice, acme);
        register(bob, beta);
    }

    @Test
    @DisplayName("초대하면 INVITED로 보이고, 초대받은 사람이 처음 로그인하면 ACTIVE가 된다")
    void inviteThenFirstSignInActivates() throws Exception {
        String email = "carol-" + run + "@acme.test";
        String body = invite(alice, acme, "LEARNER", null, email);
        assertThat((String) JsonPath.read(body, "$.results[0].outcome")).isEqualTo("INVITED");
        verify(keycloak).addMemberIfAbsent("kc-" + acme, subFor(email));
        verify(keycloak).sendSetupEmail(eq(subFor(email)), eq("lms-web"), anyString());

        String members = json(get("/v1/members").with(member(alice, acme)));
        assertThat(statusOf(members, email)).isEqualTo("INVITED");

        mvc.perform(get("/v1/me").with(member(subFor(email), acme)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("LEARNER"));

        members = json(get("/v1/members").with(member(alice, acme)));
        assertThat(statusOf(members, email)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("한 번에 여러 명을 초대하면 주소마다 결과가 따로 나온다 (가입한 멤버, 실패 포함)")
    void batchReportsEachAddress() throws Exception {
        String ok = "dave-" + run + "@acme.test";
        String broken = "broken-" + run + "@acme.test";
        given(keycloak.ensureUser(broken)).willThrow(new IllegalStateException("keycloak down"));
        invite(alice, acme, "LEARNER", null, ok);
        mvc.perform(get("/v1/me").with(member(subFor(ok), acme))).andExpect(status().isOk());   // joined → ACTIVE

        String body = invite(alice, acme, "LEARNER", null, ok, broken, "Erin-" + run + "@ACME.test");

        assertThat((List<String>) JsonPath.read(body, "$.results[*].outcome"))
                .containsExactly("ALREADY_MEMBER", "FAILED", "INVITED");
        assertThat((String) JsonPath.read(body, "$.results[2].email")).isEqualTo("erin-" + run + "@acme.test");
    }

    @Test
    @DisplayName("안내 메일 발송이 실패해도 초대는 유지되고(보상 없음), 같은 주소로 다시 초대하면 메일을 다시 보낸다")
    void emailFailureKeepsTheInvitation() throws Exception {
        String email = "mia-" + run + "@acme.test";
        org.mockito.BDDMockito.willThrow(new IllegalStateException("Invalid redirect uri"))
                .given(keycloak).sendSetupEmail(eq(subFor(email)), anyString(), anyString());

        String first = invite(alice, acme, "LEARNER", null, email);
        assertThat((String) JsonPath.read(first, "$.results[0].outcome")).isEqualTo("INVITED");
        assertThat((String) JsonPath.read(first, "$.results[0].message")).contains("메일을 보내지 못했습니다");
        verify(keycloak, never()).removeMember(anyString(), eq(subFor(email)));
        assertThat(statusOf(json(get("/v1/members").with(member(alice, acme))), email)).isEqualTo("INVITED");

        org.mockito.BDDMockito.willDoNothing().given(keycloak).sendSetupEmail(eq(subFor(email)), anyString(), anyString());
        String again = invite(alice, acme, "LEARNER", null, email);
        assertThat((String) JsonPath.read(again, "$.results[0].message")).isEqualTo("안내 메일을 다시 보냈습니다.");
        verify(keycloak, org.mockito.Mockito.times(2)).sendSetupEmail(eq(subFor(email)), eq("lms-web"), eq("http://localhost:3000/auth/login"));
    }

    @Test
    @DisplayName("이미 비밀번호가 있는 계정(다른 회사 소속)은 안내 메일 없이 초대만 된다")
    void existingAccountGetsNoPasswordEmail() throws Exception {
        String email = "noah-" + run + "@acme.test";
        given(keycloak.ensureUser(email)).willReturn(new KeycloakUser(subFor(email), false, false));

        String body = invite(alice, acme, "INSTRUCTOR", null, email);
        assertThat((String) JsonPath.read(body, "$.results[0].outcome")).isEqualTo("INVITED");
        verify(keycloak, never()).sendSetupEmail(eq(subFor(email)), anyString(), anyString());
    }

    @Test
    @DisplayName("다른 회사의 부서 ID로는 초대도, 부서 배치도 할 수 없다")
    void cannotUseAnotherCompanysDepartment() throws Exception {
        String betaDept = createDepartment(bob, beta, "인사팀");

        mvc.perform(post("/v1/invitations").with(member(alice, acme)).contentType(MediaType.APPLICATION_JSON)
                        .content(inviteJson("LEARNER", betaDept, "frank-" + run + "@acme.test")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
        verify(keycloak, never()).ensureUser("frank-" + run + "@acme.test");

        String aliceId = membershipIdOf(alice);
        mvc.perform(patch("/v1/members/{id}", aliceId).with(member(alice, acme))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"departmentId\":\"" + betaDept + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("비활성화된 멤버는 바로 403, 다시 활성화하면 들어올 수 있다")
    void disabledMemberIsLockedOut() throws Exception {
        String grace = joinedMember("grace", "LEARNER");
        String graceId = membershipIdOf(grace);

        updateMember(graceId, "{\"status\":\"DISABLED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(get("/v1/me").with(member(grace, acme)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        updateMember(graceId, "{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        mvc.perform(get("/v1/me").with(member(grace, acme))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자는 자기 권한을 스스로 내리거나 자신을 비활성화할 수 없다")
    void adminCannotDemoteSelf() throws Exception {
        String aliceId = membershipIdOf(alice);
        updateMember(aliceId, "{\"role\":\"LEARNER\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_DEMOTE_SELF"));
        updateMember(aliceId, "{\"status\":\"DISABLED\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_DEMOTE_SELF"));
    }

    @Test
    @DisplayName("두 관리자가 동시에 서로를 강등해도 관리자가 0명이 되지 않는다 (비관적 잠금)")
    void concurrentMutualDemotionKeepsAnAdmin() throws Exception {
        String henry = joinedMember("henry", "ORG_ADMIN");
        String aliceId = membershipIdOf(alice);
        String henryId = membershipIdOf(henry);

        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> aliceDemotesHenry = () -> {
            start.await();
            return demote(alice, henryId);
        };
        Callable<Integer> henryDemotesAlice = () -> {
            start.await();
            return demote(henry, aliceId);
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(aliceDemotesHenry);
            var h = pool.submit(henryDemotesAlice);
            start.countDown();
            List<Integer> codes = List.of(a.get(), h.get());
            // Exactly one wins. The loser gets 409 LAST_ADMIN (waited on the lock) or 403 (its
            // own role was already gone when its request was authenticated).
            assertThat(codes).containsOnlyOnce(200);
            assertThat(codes).anyMatch(c -> c == 409 || c == 403);
        }

        String survivor = canSee(alice) ? alice : henry;
        String members = json(get("/v1/members").with(member(survivor, acme)));
        assertThat((List<String>) JsonPath.read(members, "$[?(@.role == 'ORG_ADMIN' && @.status == 'ACTIVE')].id"))
                .hasSize(1);
    }

    @Test
    @DisplayName("초대 취소는 가입 전에만 가능하고, Keycloak 조직에서도 빠진다")
    void cancelInvitationOnlyBeforeJoining() throws Exception {
        String email = "ivy-" + run + "@acme.test";
        invite(alice, acme, "LEARNER", null, email);
        String ivyId = idOf(json(get("/v1/members").with(member(alice, acme))), email);

        mvc.perform(delete("/v1/members/{id}", ivyId).with(member(alice, acme))).andExpect(status().isNoContent());
        verify(keycloak).removeMember("kc-" + acme, subFor(email));

        String aliceId = membershipIdOf(alice);
        mvc.perform(delete("/v1/members/{id}", aliceId).with(member(alice, acme)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AN_INVITATION"));
    }

    @Test
    @DisplayName("멤버가 배치된 부서는 삭제할 수 없다 (409), 비우면 삭제된다")
    void departmentInUseCannotBeDeleted() throws Exception {
        String dept = createDepartment(alice, acme, "개발팀");
        String jack = joinedMember("jack", "LEARNER");
        String jackId = membershipIdOf(jack);
        updateMember(jackId, "{\"departmentId\":\"" + dept + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(dept));

        mvc.perform(delete("/v1/departments/{id}", dept).with(member(alice, acme)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEPARTMENT_IN_USE"));

        updateMember(jackId, "{\"unassignDepartment\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(delete("/v1/departments/{id}", dept).with(member(alice, acme))).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("학습자는 멤버 관리 API에 접근할 수 없다")
    void learnerCannotManageMembers() throws Exception {
        String kate = joinedMember("kate", "LEARNER");
        mvc.perform(get("/v1/members").with(member(kate, acme))).andExpect(status().isForbidden());
        mvc.perform(post("/v1/invitations").with(member(kate, acme)).contentType(MediaType.APPLICATION_JSON)
                .content(inviteJson("ORG_ADMIN", null, "x-" + run + "@acme.test"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("다른 회사 멤버의 ID로는 수정할 수 없다 (404)")
    void cannotTouchAnotherCompanysMember() throws Exception {
        String bobId = membershipIdOf(bob, beta);
        updateMember(bobId, "{\"role\":\"LEARNER\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    // --- helpers -------------------------------------------------------------------------------

    private static String subFor(String email) {
        return "kc-" + email.substring(0, email.indexOf('@')).toLowerCase();
    }

    /** Invite as acme's admin and sign in once, so the membership is ACTIVE. Returns the token subject. */
    private String joinedMember(String name, String role) throws Exception {
        String email = name + "-" + run + "@acme.test";
        invite(alice, acme, role, null, email);
        String sub = subFor(email);
        mvc.perform(get("/v1/me").with(member(sub, acme))).andExpect(status().isOk());
        return sub;
    }

    private int demote(String actor, String targetId) throws Exception {
        return mvc.perform(patch("/v1/members/{id}", targetId).with(member(actor, acme))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"LEARNER\"}"))
                .andReturn().getResponse().getStatus();
    }

    private boolean canSee(String sub) {
        try {
            return mvc.perform(get("/v1/members").with(member(sub, acme))).andReturn().getResponse().getStatus() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private org.springframework.test.web.servlet.ResultActions updateMember(String id, String json) throws Exception {
        return mvc.perform(patch("/v1/members/{id}", id).with(member(alice, acme))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String membershipIdOf(String sub) throws Exception {
        return membershipIdOf(sub, acme);
    }

    private String membershipIdOf(String sub, String org) throws Exception {
        String admin = org.equals(acme) ? alice : bob;
        String members = json(get("/v1/members").with(member(admin, org)));
        List<String> ids = JsonPath.read(members, "$[?(@.email == '" + emailOf(sub) + "')].id");
        assertThat(ids).as("membership of %s", sub).hasSize(1);
        return ids.getFirst();
    }

    /** The email our app stores: refreshed from the token on every sign-in. */
    private static String emailOf(String sub) {
        return sub + "@example.com";
    }

    private static String idOf(String members, String email) {
        List<String> ids = JsonPath.read(members, "$[?(@.email == '" + email + "')].id");
        return ids.getFirst();
    }

    private static String statusOf(String members, String email) {
        List<String> statuses = JsonPath.read(members, "$[?(@.email == '" + email + "' || @.email == '"
                + subFor(email) + "@example.com')].status");
        assertThat(statuses).hasSize(1);
        return statuses.getFirst();
    }

    private String invite(String sub, String org, String role, String departmentId, String... emails) throws Exception {
        return json(post("/v1/invitations").with(member(sub, org)).contentType(MediaType.APPLICATION_JSON)
                .content(inviteJson(role, departmentId, emails)));
    }

    private static String inviteJson(String role, String departmentId, String... emails) {
        String list = String.join("\",\"", emails);
        return "{\"emails\":[\"" + list + "\"],\"role\":\"" + role + "\""
                + (departmentId == null ? "" : ",\"departmentId\":\"" + departmentId + "\"") + "}";
    }

    private String json(RequestBuilder request) throws Exception {
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString(StandardCharsets.UTF_8)).isBetween(200, 299);
        return response.getContentAsString(StandardCharsets.UTF_8);
    }

    private void register(String sub, String slug) throws Exception {
        mvc.perform(post("/v1/tenants").with(user(sub)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + slug + "\",\"slug\":\"" + slug + "\",\"domain\":\"" + slug + ".test\"}"))
                .andExpect(status().isCreated());
    }

    private String createDepartment(String sub, String org, String name) throws Exception {
        return JsonPath.read(json(post("/v1/departments").with(member(sub, org))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}")), "$.id");
    }
}
