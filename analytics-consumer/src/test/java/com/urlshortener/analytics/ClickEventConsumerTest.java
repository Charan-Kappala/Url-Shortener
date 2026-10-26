package com.urlshortener.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.analytics.listener.ClickEventConsumer;
import com.urlshortener.analytics.model.ClickEventMessage;
import com.urlshortener.analytics.service.ClickEventWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real consumer against Postgres (schema from core-api's migrations) and SQS in LocalStack. */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=filesystem:../core-api/src/main/resources/db/migration"})
class ClickEventConsumerTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
                    .withServices(LocalStackContainer.Service.SQS);

    static {
        POSTGRES.start();
        LOCALSTACK.start();
        System.setProperty("aws.accessKeyId", LOCALSTACK.getAccessKey());
        System.setProperty("aws.secretAccessKey", LOCALSTACK.getSecretKey());
    }

    @DynamicPropertySource
    static void sqsProperties(DynamicPropertyRegistry registry) throws Exception {
        String queueUrl = SqsAsyncClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(software.amazon.awssdk.regions.Region.of(LOCALSTACK.getRegion()))
                .build()
                .createQueue(r -> r.queueName("click-events")).get().queueUrl();
        registry.add("aws.endpoint-url", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("aws.region", LOCALSTACK::getRegion);
        registry.add("aws.sqs.click-events-queue-url", () -> queueUrl);
    }

    @Autowired SqsAsyncClient sqs;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClickEventConsumer consumer;
    @Autowired ClickEventWriter writer;
    @org.springframework.beans.factory.annotation.Value("${aws.sqs.click-events-queue-url}") String queueUrl;

    @Test
    void everyEventIsCountedExactlyOnceWhenTheConsumerRestartsMidStream() throws Exception {
        String code = newLink();
        int total = 1000;

        CompletableFuture<Void> producer = CompletableFuture.runAsync(() -> send(events(code, total)));

        waitUntil(() -> clickCount(code) >= 300, Duration.ofSeconds(60));
        consumer.stop();   // like a deploy or crash: in-flight messages are not deleted and come back later
        Thread.sleep(1000);
        consumer.start();

        producer.get();
        waitUntil(() -> clickCount(code) == total && queueIsEmpty(), Duration.ofSeconds(90));
        Thread.sleep(2000); // nothing more may arrive, e.g. a late redelivery counted twice
        assertThat(clickCount(code)).isEqualTo(total);
        assertThat(storedEvents(code)).isEqualTo(total);
    }

    @Test
    void eventCommittedButNotDeletedFromQueueIsNotCountedTwice() throws Exception {
        String code = newLink();
        consumer.stop();
        send(events(code, 10));

        // Simulate a consumer that crashes after the DB commit but before deleting the messages:
        // receive with a 1 s visibility timeout, write, never delete.
        List<Message> received = sqs.receiveMessage(r -> r.queueUrl(queueUrl)
                .maxNumberOfMessages(10).visibilityTimeout(1).waitTimeSeconds(5)).get().messages();
        List<ClickEventMessage> batch = new ArrayList<>();
        for (Message m : received) batch.add(json.readValue(m.body(), ClickEventMessage.class));
        assertThat(writer.write(batch)).isEqualTo(received.size());

        consumer.start(); // the messages reappear and are processed a second time
        waitUntil(this::queueIsEmpty, Duration.ofSeconds(30));
        assertThat(clickCount(code)).isEqualTo(10);
        assertThat(storedEvents(code)).isEqualTo(10);
    }

    private String newLink() {
        String code = "t" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO url_mappings (short_code, original_url) VALUES (?, 'https://example.com')", code);
        return code;
    }

    private static List<ClickEventMessage> events(String code, int n) {
        List<ClickEventMessage> events = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ClickEventMessage e = new ClickEventMessage();
            e.setEventId(UUID.randomUUID());
            e.setShortCode(code);
            e.setClickedAt(Instant.now());
            e.setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148");
            events.add(e);
        }
        return events;
    }

    private void send(List<ClickEventMessage> events) {
        try {
            for (int i = 0; i < events.size(); i += 10) {
                List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
                for (ClickEventMessage e : events.subList(i, Math.min(i + 10, events.size()))) {
                    entries.add(SendMessageBatchRequestEntry.builder()
                            .id(e.getEventId().toString()).messageBody(json.writeValueAsString(e)).build());
                }
                sqs.sendMessageBatch(r -> r.queueUrl(queueUrl).entries(entries)).get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long clickCount(String code) {
        return jdbc.queryForObject("SELECT click_count FROM url_mappings WHERE short_code = ?", Long.class, code);
    }

    private long storedEvents(String code) {
        return jdbc.queryForObject("SELECT count(*) FROM click_events WHERE short_code = ?", Long.class, code);
    }

    private boolean queueIsEmpty() {
        try {
            var attrs = sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).get().attributes();
            return attrs.values().stream().allMatch("0"::equals);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) throw new AssertionError("Condition not met within " + timeout);
            Thread.sleep(200);
        }
    }
}
