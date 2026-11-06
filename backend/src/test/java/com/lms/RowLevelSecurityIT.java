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

    @BeforeEach
    void seed() {
        tenantA = insertTenant();
        tenantB = insertTenant();
        TenantContext.runAs(tenantA, () -> tx.executeWithoutResult(s -> insertDepartment(tenantA, "A-개발")));
        TenantContext.runAs(tenantB, () -> tx.executeWithoutResult(s -> insertDepartment(tenantB, "B-개발")));
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

    private void insertDepartment(UUID tenantId, String name) {
        jdbc.sql("insert into department (id, tenant_id, name) values (?, ?, ?)")
                .params(UuidV7.generate(), tenantId, name).update();
    }
}
