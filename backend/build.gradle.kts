plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.lms"
version = "0.0.1-SNAPSHOT"
description = "B2B multi-tenant LMS API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.1.1"

dependencies {
    // Web + API docs
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")

    // Security: the API is an OAuth2 resource server (validates Keycloak JWTs, never logs anyone in)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // Persistence: JPA (Hibernate @TenantId) + Flyway (SQL migrations incl. RLS) + PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Redis (Valkey): membership cache, rate limits, BFF sessions live in the same cluster
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Modular monolith boundaries
    implementation("org.springframework.modulith:spring-modulith-starter-core")

    // AWS SDK v2. Locally: S3 → Adobe S3Mock, SQS → ElasticMQ (both API-compatible stand-ins).
    implementation(platform("software.amazon.awssdk:bom:2.46.7"))
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:mediaconvert")
    implementation("software.amazon.awssdk:cloudfront")   // signed cookies for HLS

    // Outbox: Spring Modulith's event publication registry (events stored in the business transaction)
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

    // Certificates and mail: HTML templates (Thymeleaf) → PDF (OpenHTMLtoPDF, LGPL; iText is AGPL)
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.108")

    // Dashboard cache (Redis in the app, an in-memory cache in tests) and scheduled jobs that run
    // on exactly one instance even when several are deployed (ShedLock, lock rows in PostgreSQL)
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.6.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:6.6.0")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

tasks.withType<JavaCompile> {
    // Keep parameter names so Spring can bind @PathVariable/@RequestParam without explicit names
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // One line per test with its @DisplayName (the scenario in Korean), full trace on failure.
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
