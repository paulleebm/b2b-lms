package com.lms.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * ElasticMQ (SQS API) with the same queue/DLQ definitions as local Docker Compose, and Mailpit
 * (SMTP + an HTTP API to read what was sent). Async tests assert real outcomes: the message went
 * through a queue, the PDF exists, the mail arrived.
 */
public final class AsyncContainers {

    @SuppressWarnings("resource")
    private static final GenericContainer<?> SQS = new GenericContainer<>(DockerImageName.parse("softwaremill/elasticmq-native:1.7.1"))
            .withCopyFileToContainer(MountableFile.forHostPath("../infra/local/elasticmq.conf"), "/opt/elasticmq.conf")
            .withExposedPorts(9324)
            .waitingFor(Wait.forListeningPort());

    @SuppressWarnings("resource")
    private static final GenericContainer<?> MAIL = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:latest"))
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forListeningPort());

    static {
        SQS.start();
        MAIL.start();
    }

    private AsyncContainers() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("lms.messaging.endpoint", () -> "http://" + SQS.getHost() + ":" + SQS.getMappedPort(9324));
        registry.add("spring.mail.host", MAIL::getHost);
        registry.add("spring.mail.port", () -> MAIL.getMappedPort(1025));
    }

    public static String sqsEndpoint() {
        return "http://" + SQS.getHost() + ":" + SQS.getMappedPort(9324);
    }

    /** Mailpit search API: messages to this address (JSON). */
    public static String mailsTo(String address) {
        try {
            String url = "http://" + MAIL.getHost() + ":" + MAIL.getMappedPort(8025) + "/api/v1/search?query="
                    + java.net.URLEncoder.encode("to:\"" + address + "\"", java.nio.charset.StandardCharsets.UTF_8);
            return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
