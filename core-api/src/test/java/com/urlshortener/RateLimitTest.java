package com.urlshortener;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static com.urlshortener.ShortCodeConcurrencyTest.runInParallel;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.rate-limit.create-per-minute=" + RateLimitTest.LIMIT)
class RateLimitTest extends IntegrationTest {

    static final int LIMIT = 20; // bucket capacity; refills at LIMIT per minute

    @Autowired TestRestTemplate http;

    static ConfigurableApplicationContext secondInstance;
    static String secondInstanceUrl;

    @BeforeAll
    static void startSecondInstance() {
        // A second, independent copy of the app (own JVM objects, own connection pools) sharing the same Redis.
        secondInstance = new SpringApplicationBuilder(UrlShortenerApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.data.redis.host=" + REDIS.getHost(),
                "--spring.data.redis.port=" + REDIS.getMappedPort(6379),
                "--aws.region=" + LOCALSTACK.getRegion(),
                "--aws.endpoint-url=" + LOCALSTACK.getEndpoint(),
                "--aws.sqs.click-events-queue-url=" + QUEUE_URL,
                "--app.rate-limit.create-per-minute=" + LIMIT);
        secondInstanceUrl = "http://localhost:" + secondInstance.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stopSecondInstance() {
        secondInstance.close();
    }

    @Test
    void parallelBurstFromOneClientIsCappedAtBucketCapacity() throws Exception {
        long start = System.nanoTime();
        List<ResponseEntity<Map>> responses = runInParallel(50, 100, i -> create("", "10.0.0.1"));
        long allowed = countCreated(responses);

        assertThat(allowed).isBetween((long) LIMIT, LIMIT + refilledSince(start));
        ResponseEntity<Map> rejected = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS).findFirst().orElseThrow();
        assertThat(Integer.parseInt(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))).isBetween(1, 60);
    }

    @Test
    void limitIsSharedAcrossTwoAppInstances() throws Exception {
        long start = System.nanoTime();
        // Same client, requests split evenly between the two instances.
        List<ResponseEntity<Map>> responses = runInParallel(50, 100,
                i -> create(i % 2 == 0 ? "" : secondInstanceUrl, "10.0.0.2"));

        assertThat(countCreated(responses)).isBetween((long) LIMIT, LIMIT + refilledSince(start));
    }

    @Test
    void differentClientsHaveSeparateBuckets() throws Exception {
        List<ResponseEntity<Map>> a = runInParallel(10, LIMIT, i -> create("", "10.0.0.3"));
        List<ResponseEntity<Map>> b = runInParallel(10, LIMIT, i -> create(secondInstanceUrl, "10.0.0.4"));

        assertThat(countCreated(a)).isEqualTo(LIMIT);
        assertThat(countCreated(b)).isEqualTo(LIMIT);
    }

    private ResponseEntity<Map> create(String baseUrl, String clientIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", clientIp); // trusted because the test client connects from localhost
        return http.postForEntity(baseUrl + "/api/v1/urls",
                new HttpEntity<>(Map.of("originalUrl", "https://example.com"), headers), Map.class);
    }

    private static long countCreated(List<ResponseEntity<Map>> responses) {
        return responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
    }

    /** Upper bound on tokens added back while the test ran (LIMIT per minute), rounded up. */
    private static long refilledSince(long startNanos) {
        double seconds = (System.nanoTime() - startNanos) / 1e9;
        return (long) Math.ceil(seconds * LIMIT / 60.0);
    }
}
