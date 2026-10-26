package com.urlshortener;

import com.urlshortener.service.ShortCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeConcurrencyTest extends IntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void parallelCreatesAllGetDistinctCodes() throws Exception {
        int requests = 2000;
        List<ResponseEntity<Map>> responses = runInParallel(64, requests, i ->
                http.postForEntity("/api/v1/urls", Map.of("originalUrl", "https://example.com/p/" + i), Map.class));

        Set<String> codes = new HashSet<>();
        for (ResponseEntity<Map> r : responses) {
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            codes.add((String) r.getBody().get("shortCode"));
        }
        assertThat(codes).hasSize(requests);
        assertThat(codes).allMatch(c -> c.matches("[0-9A-Za-z]{7}"));
    }

    @Test
    void twoGeneratorsSharingTheSequenceNeverOverlap() throws Exception {
        // Two generator objects behave like two app instances: separate in-memory blocks, one shared sequence.
        ShortCodeGenerator instanceA = new ShortCodeGenerator(jdbc);
        ShortCodeGenerator instanceB = new ShortCodeGenerator(jdbc);
        int perInstance = 20_000;

        List<String> codes = runInParallel(32, 2 * perInstance,
                i -> (i % 2 == 0 ? instanceA : instanceB).nextCode());

        assertThat(new HashSet<>(codes)).hasSize(2 * perInstance);
    }

    @Test
    void sameCustomAliasRequestedConcurrentlyIsCreatedExactlyOnce() throws Exception {
        String alias = "race-" + System.nanoTime() % 100_000;
        List<ResponseEntity<Map>> responses = runInParallel(20, 20, i ->
                http.postForEntity("/api/v1/urls",
                        Map.of("originalUrl", "https://example.com/" + i, "customCode", alias), Map.class));

        long created = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count();
        long conflicts = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(19);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM url_mappings WHERE short_code = ?", Long.class, alias))
                .isEqualTo(1);
    }

    interface Task<T> { T run(int i) throws Exception; }

    static <T> List<T> runInParallel(int threads, int tasks, Task<T> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<T>> calls = new ArrayList<>();
            for (int i = 0; i < tasks; i++) {
                int n = i;
                calls.add(() -> task.run(n));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> f : pool.invokeAll(calls)) results.add(f.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
