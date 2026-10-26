package com.urlshortener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.urlshortener.ShortCodeConcurrencyTest.runInParallel;
import static org.assertj.core.api.Assertions.assertThat;

class RedirectClickEventTest extends IntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;

    @Test
    void everyRedirectPublishesExactlyOneClickEvent() throws Exception {
        String code = (String) http.postForEntity("/api/v1/urls",
                Map.of("originalUrl", "https://example.com/clicks"), Map.class).getBody().get("shortCode");
        int redirects = 500;

        List<ResponseEntity<Void>> responses = runInParallel(32, redirects, i -> http.getForEntity("/" + code, Void.class));
        assertThat(responses).allMatch(r -> r.getStatusCode() == HttpStatus.FOUND);

        // Drain the queue until all events for this code have arrived (publishing is asynchronous).
        Set<String> eventIds = new HashSet<>();
        int eventsForCode = 0;
        long deadline = System.currentTimeMillis() + 30_000;
        try (SqsClient sqs = sqsClient()) {
            while (eventsForCode < redirects && System.currentTimeMillis() < deadline) {
                List<Message> batch = sqs.receiveMessage(r -> r.queueUrl(QUEUE_URL)
                        .maxNumberOfMessages(10).waitTimeSeconds(1)).messages();
                for (Message m : batch) {
                    JsonNode event = json.readTree(m.body());
                    if (code.equals(event.get("shortCode").asText())) {
                        eventsForCode++;
                        eventIds.add(event.get("eventId").asText());
                    }
                    sqs.deleteMessage(r -> r.queueUrl(QUEUE_URL).receiptHandle(m.receiptHandle()));
                }
            }
        }

        assertThat(eventsForCode).isEqualTo(redirects);
        assertThat(eventIds).hasSize(redirects);
    }
}
