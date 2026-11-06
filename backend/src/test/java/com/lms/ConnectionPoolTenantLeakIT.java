package com.lms;

import static org.assertj.core.api.Assertions.assertThat;

import com.lms.shared.TenantContext;
import com.lms.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The classic RLS + connection-pool bug: request A sets the tenant on a pooled connection,
 * returns it, and request B (another company) gets the same physical connection.
 * With a pool of ONE connection, the second checkout is guaranteed to be the same one.
 */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class ConnectionPoolTenantLeakIT extends IntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    TransactionTemplate tx;

    @Test
    @DisplayName("커넥션을 풀에 돌려줄 때 tenant 설정이 지워져 다음 요청으로 새지 않는다")
    void tenantDoesNotLeakToTheNextBorrower() {
        UUID tenantA = UUID.randomUUID();

        String duringA = TenantContext.callAs(tenantA, () -> tx.execute(s -> currentSetting()));
        assertThat(duringA).isEqualTo(tenantA.toString());

        String afterwards = tx.execute(s -> currentSetting());   // same physical connection, no tenant
        assertThat(afterwards).isNullOrEmpty();

        UUID tenantB = UUID.randomUUID();
        String duringB = TenantContext.callAs(tenantB, () -> tx.execute(s -> currentSetting()));
        assertThat(duringB).isEqualTo(tenantB.toString());
    }

    private String currentSetting() {
        return jdbc.sql("select current_setting('app.tenant_id', true)").query(String.class).single();
    }
}
