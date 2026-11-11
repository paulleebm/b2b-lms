package com.lms.audit.internal;

import com.lms.shared.web.ApiNullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Recent audit entries for the company's admins (RLS scopes the rows to the company). */
@RestController
@PreAuthorize("hasRole('ORG_ADMIN')")
class AuditController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    AuditController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param actorName null = the system (scheduled job, new-hire sync) */
    record AuditEntry(UUID id, Instant at, @ApiNullable String actorName, String action, String targetType,
                      @ApiNullable UUID targetId, JsonNode metadata, @ApiNullable String traceId) {
    }

    @GetMapping("/v1/audit-log")
    @Transactional(readOnly = true)
    List<AuditEntry> recent(@RequestParam(defaultValue = "100") int limit) {
        return jdbc.sql("""
                        select a.id, a.created_at, coalesce(u.name, u.email), a.action, a.target_type, a.target_id,
                               a.metadata::text, a.trace_id
                        from audit_log a left join app_user u on u.id = a.actor_user_id
                        order by a.created_at desc limit ?
                        """)
                .param(Math.max(1, Math.min(limit, 500)))
                .query((rs, n) -> new AuditEntry(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class),
                        JSON.readTree(rs.getString(7)), rs.getString(8)))
                .list();
    }
}
