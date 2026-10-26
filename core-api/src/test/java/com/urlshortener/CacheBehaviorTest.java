package com.urlshortener;

import com.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CacheBehaviorTest extends IntegrationTest {

    @Autowired TestRestTemplate http;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @SpyBean UrlMappingRepository repository;

    @Test
    void unknownCodeIsLookedUpInPostgresOnceThenServedFromTheNegativeCache() {
        String code = "nope" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 50; i++) {
            assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        verify(repository, times(1)).findByShortCode(code);
        assertThat(redis.getExpire("url:" + code, TimeUnit.SECONDS)).isBetween(1L, 60L);
    }

    @Test
    void creatingAnAliasClearsAnEarlierNotFoundTombstone() {
        String alias = "late" + UUID.randomUUID().toString().substring(0, 6);
        assertThat(http.getForEntity("/" + alias, Void.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        http.postForEntity("/api/v1/urls", Map.of("originalUrl", "https://example.com/late", "customCode", alias), Map.class);

        assertThat(http.getForEntity("/" + alias, Void.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void cachedLinkNeverOutlivesItsExpiry() throws Exception {
        String code = "exp" + UUID.randomUUID().toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO url_mappings (short_code, original_url, expires_at)
                VALUES (?, 'https://example.com/soon', now() + interval '3 seconds')""", code);

        assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redis.getExpire("url:" + code, TimeUnit.MILLISECONDS)).isBetween(1L, 3000L);

        Thread.sleep(3500);
        assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.GONE);
    }

    @Test
    void linkTtlsAreJitteredAroundOneHour() {
        List<Long> ttls = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            String code = (String) http.postForEntity("/api/v1/urls",
                    Map.of("originalUrl", "https://example.com/j" + i), Map.class).getBody().get("shortCode");
            http.getForEntity("/" + code, Void.class);
            ttls.add(redis.getExpire("url:" + code, TimeUnit.SECONDS));
        }
        assertThat(ttls).allMatch(t -> t >= 54 * 60 && t <= 66 * 60);
        assertThat(new HashSet<>(ttls).size()).isGreaterThan(20); // not all expiring in the same second
    }
}
