package com.lms.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Adobe S3Mock for upload tests: real S3 API calls (presign, multipart, HeadObject) against an
 * S3-compatible server. It does not verify signatures, so the tests prove our flow and checks,
 * not AWS's signature enforcement.
 */
public final class S3MockContainer {

    public static final String BUCKET = "lms-media-test";

    @SuppressWarnings("resource")
    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                    .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
                    .withExposedPorts(9090)
                    .waitingFor(Wait.forListeningPort());

    static {
        CONTAINER.start();
    }

    private S3MockContainer() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("lms.storage.endpoint", () -> "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9090));
        registry.add("lms.storage.bucket", () -> BUCKET);
    }
}
