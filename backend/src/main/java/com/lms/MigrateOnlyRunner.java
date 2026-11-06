package com.lms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * {@code --lms.migrate-only=true}: start, let Flyway migrate (it runs during startup, as the
 * owner account), then exit with the result. In AWS this is the one-off ECS task CI runs before a
 * rollout, so the long-running API never holds the owner credentials.
 */
@Component
@ConditionalOnProperty(name = "lms.migrate-only", havingValue = "true")
class MigrateOnlyRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrateOnlyRunner.class);

    private final ApplicationContext context;

    MigrateOnlyRunner(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Migrations applied; exiting (migrate-only mode)");
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
