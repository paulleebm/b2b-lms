package com.lms.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One real PostgreSQL for the whole test run (started once, reused by every test class).
 *
 * <p>Why not H2 or a mock? Row-level security, the SECURITY DEFINER function and the role setup
 * exist only in real Postgres — the most important tests here would prove nothing elsewhere.
 *
 * <p>The container runs the same role bootstrap as local Docker Compose
 * ({@code infra/local/postgres/init/01-roles.sql}), so tests connect exactly like the app:
 * as {@code lms_app} (RLS applies) while Flyway migrates as {@code lms_owner}.
 */
public final class PostgresContainer {

    private static final PostgreSQLContainer CONTAINER =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
                    .withCopyFileToContainer(
                            MountableFile.forHostPath("../infra/local/postgres/init/01-roles.sql"),
                            "/docker-entrypoint-initdb.d/01-roles.sql");

    static {
        CONTAINER.start();
    }

    private PostgresContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        String url = "jdbc:postgresql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(5432) + "/lms";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "lms_app");
        registry.add("spring.datasource.password", () -> "lms_app_dev");
        registry.add("spring.flyway.user", () -> "lms_owner");
        registry.add("spring.flyway.password", () -> "lms_owner_dev");
    }
}
