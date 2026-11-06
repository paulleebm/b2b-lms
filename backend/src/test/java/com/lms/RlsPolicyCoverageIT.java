package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Guards future migrations: any table with a {@code tenant_id} column must have RLS enabled,
 * FORCEd, and a policy. Forgetting this on a new table fails the build instead of leaking data.
 */
class RlsPolicyCoverageIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("tenant_id 컬럼이 있는 모든 테이블에 RLS가 켜져 있고(FORCE 포함) 정책이 있다")
    void everyTenantTableIsProtected() {
        List<String> unprotected = jdbc.sql("""
                select c.relname
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
                join pg_attribute a on a.attrelid = c.oid and a.attname = 'tenant_id' and not a.attisdropped
                where c.relkind = 'r'
                  and (not c.relrowsecurity
                       or not c.relforcerowsecurity
                       or not exists (select 1 from pg_policy p where p.polrelid = c.oid))
                order by 1
                """).query(String.class).list();

        assertThat(unprotected).as("tables with tenant_id but without enforced RLS").isEmpty();
    }

    @Test
    @DisplayName("앱 계정(lms_app)은 테이블 소유자도, RLS 우회 권한자도 아니다")
    void appRoleCannotBypassRls() {
        Boolean bypass = jdbc.sql("select rolbypassrls or rolsuper from pg_roles where rolname = current_user")
                .query(Boolean.class).single();
        Long ownedTables = jdbc.sql("select count(*) from pg_tables where schemaname = 'public' and tableowner = current_user")
                .query(Long.class).single();

        assertThat(jdbc.sql("select current_user").query(String.class).single()).isEqualTo("lms_app");
        assertThat(bypass).isFalse();
        assertThat(ownedTables).isZero();
    }
}
