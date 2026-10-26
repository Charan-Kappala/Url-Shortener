package com.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Per-client token bucket kept in Redis, so every app instance shares the same buckets.
 *
 * A bucket holds up to {@code perMinute} tokens (the allowed burst) and refills continuously at
 * {@code perMinute / 60} tokens per second. Each request takes one token. The read-refill-take-write
 * sequence runs as one Lua script, which Redis executes atomically: two instances checking the same
 * client at the same moment cannot both spend the last token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> TOKEN_BUCKET =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final StringRedisTemplate redis;

    public record Decision(boolean allowed, long retryAfterMillis) {}

    @SuppressWarnings("unchecked")
    public Decision tryAcquire(String action, String clientId, int perMinute) {
        try {
            List<Long> result = redis.execute(TOKEN_BUCKET,
                    List.of("ratelimit:" + action + ":" + clientId),
                    String.valueOf(perMinute), String.valueOf(perMinute / 60.0));
            return new Decision(result.get(0) == 1L, result.get(1));
        } catch (Exception e) {
            // Fail open: if Redis is unavailable, serving traffic matters more than limiting it.
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return new Decision(true, 0);
        }
    }
}
