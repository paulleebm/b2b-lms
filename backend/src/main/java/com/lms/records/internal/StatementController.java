package com.lms.records.internal;

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

/**
 * Read side of the learning record, shaped like the xAPI Statement API
 * ({@code GET statements?registration=…} → {@code {"statements": [...]}}), so an LRS client or an
 * auditor can read it as-is. Admins only; tenant-scoped by RLS like everything else.
 */
@RestController
@PreAuthorize("hasRole('ORG_ADMIN')")
class StatementController {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    StatementController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record StatementResult(List<JsonNode> statements) {
    }

    /** @param registration the enrollment id (xAPI context.registration) */
    @GetMapping("/v1/xapi/statements")
    @Transactional(readOnly = true)
    StatementResult statements(@RequestParam UUID registration,
                               @RequestParam(defaultValue = "200") int limit) {
        List<JsonNode> rows = jdbc.sql("""
                        select statement::text from learning_record
                        where enrollment_id = ? order by "timestamp", stored limit ?
                        """)
                .params(registration, Math.max(1, Math.min(limit, 500)))
                .query((rs, n) -> JSON.readTree(rs.getString(1)))
                .list();
        return new StatementResult(rows);
    }
}
