package com.lms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lms.shared.TenantContext;
import com.lms.shared.UuidV7;
import com.lms.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The last line of defense, tested below the application: even raw SQL that bypasses Hibernate's
 * {@code @TenantId} filter cannot cross tenants, because Postgres itself enforces the policy.
 */
class RowLevelSecurityIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    UUID tenantA;
    UUID tenantB;
    UUID departmentB;

    @BeforeEach
    void seed() {
        tenantA = insertTenant();
        tenantB = insertTenant();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> insertDepartment(tenantA, "A-개발")));
        departmentB = TenantContext.callAs(tenantB, () -> tx.execute(s -> insertDepartment(tenantB, "B-개발")));
    }

    @Test
    @DisplayName("외래키 검사는 RLS를 우회하므로, (tenant_id, id) 복합 외래키가 다른 회사 부서 참조를 막는다")
    void foreignKeyCannotPointIntoAnotherTenant() {
        UUID userId = UuidV7.generate();
        jdbc.sql("insert into app_user (id, keycloak_sub, email) values (?, ?, ?)")
                .params(userId, "sub-" + userId, userId + "@example.com").update();

        // B's department id is guessed or leaked; A's session cannot even see it, but a plain
        // FK on department(id) would still accept it. The composite key does not.
        assertThatThrownBy(() -> TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s ->
                jdbc.sql("insert into membership (id, tenant_id, user_id, role, status, department_id) values (?, ?, ?, 'LEARNER', 'ACTIVE', ?)")
                        .params(UuidV7.generate(), tenantA, userId, departmentB).update())))
                .rootCause()
                .hasMessageContaining("membership_department_fk");
    }

    @Test
    @DisplayName("WHERE 절에 다른 회사 ID를 직접 써도 RLS가 0건으로 만든다")
    void rawSqlCannotReadAnotherTenant() {
        Long visible = TenantContext.callAs(tenantA, () -> tx.execute(s ->
                jdbc.sql("select count(*) from department where tenant_id = ?").param(tenantB).query(Long.class).single()));
        assertThat(visible).isZero();
    }

    @Test
    @DisplayName("다른 회사 ID로 INSERT 하면 RLS의 WITH CHECK가 거부한다")
    void rawSqlCannotWriteIntoAnotherTenant() {
        assertThatThrownBy(() -> TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s ->
                insertDepartment(tenantB, "침투"))))
                // Spring translates SQLState 42501 into its own exception; the original Postgres
                // error is the root cause.
                .rootCause()
                .hasMessageContaining("new row violates row-level security policy for table \"department\"");
    }

    @Test
    @DisplayName("테넌트 컨텍스트가 없으면 테넌트 테이블은 아무 행도 보이지 않는다 (fail-closed)")
    void noContextSeesNothing() {
        Long visible = tx.execute(s -> jdbc.sql("select count(*) from department").query(Long.class).single());
        assertThat(visible).isZero();
    }

    @Test
    @DisplayName("자기 회사 데이터는 정상적으로 보인다")
    void ownTenantIsVisible() {
        List<String> names = TenantContext.callAs(tenantA, () -> tx.execute(s ->
                jdbc.sql("select name from department").query(String.class).list()));
        assertThat(names).containsExactly("A-개발");
    }

    private UUID insertTenant() {
        UUID id = UuidV7.generate();
        String slug = "t-" + id.toString().substring(28);
        jdbc.sql("insert into tenant (id, name, slug, keycloak_org_id) values (?, ?, ?, ?)")
                .params(id, "Tenant " + slug, slug, "kc-" + id).update();
        return id;
    }

    private UUID insertDepartment(UUID tenantId, String name) {
        UUID id = UuidV7.generate();
        jdbc.sql("insert into department (id, tenant_id, name) values (?, ?, ?)")
                .params(id, tenantId, name).update();
        return id;
    }
}
