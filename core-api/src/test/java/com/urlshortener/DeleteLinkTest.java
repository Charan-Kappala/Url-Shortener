package com.urlshortener;

import com.urlshortener.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.admin-api-key=" + DeleteLinkTest.ADMIN_KEY)
class DeleteLinkTest extends IntegrationTest {

    static final String ADMIN_KEY = "test-admin-key";

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired CacheService cache;

    @Test
    void deleteWithoutTheAdminKeyIsRejected() {
        String code = createAndWarmCache();

        assertThat(delete(code, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete(code, "wrong").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void deletedLinkStopsResolvingImmediatelyEvenThoughItWasCached() {
        String code = createAndWarmCache();

        assertThat(delete(code, ADMIN_KEY).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT is_active FROM url_mappings WHERE short_code = ?", Boolean.class, code))
                .isFalse();
        assertThat(delete(code, ADMIN_KEY).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aSlowReaderCannotPutADeletedLinkBackIntoTheCache() {
        String code = createAndWarmCache();
        delete(code, ADMIN_KEY);

        // A reader that loaded the row just before the delete committed now tries to cache it.
        cache.cacheLink(code, "https://example.com/stale", null);

        assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String createAndWarmCache() {
        String code = (String) http.postForEntity("/api/v1/urls",
                Map.of("originalUrl", "https://example.com/del"), Map.class).getBody().get("shortCode");
        assertThat(http.getForEntity("/" + code, Void.class).getStatusCode()).isEqualTo(HttpStatus.FOUND);
        return code;
    }

    private ResponseEntity<Map> delete(String code, String adminKey) {
        HttpHeaders headers = new HttpHeaders();
        if (adminKey != null) headers.set("X-Admin-Key", adminKey);
        return http.exchange("/api/v1/urls/" + code, HttpMethod.DELETE, new HttpEntity<>(headers), Map.class);
    }
}
