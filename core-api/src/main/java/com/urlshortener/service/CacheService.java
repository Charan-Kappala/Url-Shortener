package com.urlshortener.service;

import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cache-aside store for redirects: key "url:{code}" holds the original URL, or a tombstone saying the
 * code does not exist (NOT_FOUND) or has expired (GONE). Tombstones stop repeated requests for bad codes
 * from reaching Postgres. Original URLs always start with http(s), so they cannot clash with "!" markers.
 *
 * Readers fill the cache with SET NX (only if absent); writers (create/delete) overwrite or delete.
 * So a reader that loaded a row just before it was deleted cannot overwrite the delete's tombstone.
 *
 * Every Redis error is swallowed: the cache is an optimisation and Postgres stays the source of truth.
 */
@Slf4j
@Service
public class CacheService {

    public static final String NOT_FOUND = "!404";
    public static final String GONE = "!410";

    private static final String PREFIX = "url:";
    static final Duration LINK_TTL = Duration.ofHours(1);
    static final Duration NOT_FOUND_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final boolean enabled;

    public CacheService(StringRedisTemplate redis,
                        Counter cacheHitCounter,
                        Counter cacheMissCounter,
                        @Value("${app.cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.cacheHitCounter = cacheHitCounter;
        this.cacheMissCounter = cacheMissCounter;
        this.enabled = enabled;
    }

    /** The cached URL or tombstone for this code, or empty on a cache miss. */
    public Optional<String> get(String shortCode) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.opsForValue().get(PREFIX + shortCode);
            (value != null ? cacheHitCounter : cacheMissCounter).increment();
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("Redis GET failed for {}, falling back to Postgres: {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Caches a live link. The TTL is one hour with +/-10% jitter, so links cached together (e.g. right after
     * a restart) do not all expire in the same instant and stampede Postgres, and it never outlives the
     * link's own expiry, so an expired link drops out of the cache on time.
     */
    public void cacheLink(String shortCode, String originalUrl, ZonedDateTime expiresAt) {
        Duration ttl = jittered(LINK_TTL);
        if (expiresAt != null) {
            Duration untilExpiry = Duration.between(ZonedDateTime.now(), expiresAt);
            if (untilExpiry.compareTo(ttl) < 0) ttl = untilExpiry;
        }
        if (!ttl.isNegative() && !ttl.isZero()) {
            setIfAbsent(shortCode, originalUrl, ttl);
        }
    }

    /** Negative caching: a short TTL, so a code that gets created soon after is not hidden for long. */
    public void cacheNotFound(String shortCode) {
        setIfAbsent(shortCode, NOT_FOUND, NOT_FOUND_TTL);
    }

    public void cacheGone(String shortCode) {
        setIfAbsent(shortCode, GONE, jittered(LINK_TTL));
    }

    /** Writer path (delete): overwrite whatever is cached so the link stops resolving immediately. */
    public void markNotFound(String shortCode) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(PREFIX + shortCode, NOT_FOUND, LINK_TTL);
        } catch (Exception e) {
            log.warn("Redis SET failed for {}: {}", shortCode, e.getMessage());
        }
    }

    /** Writer path (create): drop any NOT_FOUND tombstone left by earlier requests for this code. */
    public void evict(String shortCode) {
        if (!enabled) return;
        try {
            redis.delete(PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis DEL failed for {}: {}", shortCode, e.getMessage());
        }
    }

    private void setIfAbsent(String shortCode, String value, Duration ttl) {
        if (!enabled) return;
        try {
            redis.opsForValue().setIfAbsent(PREFIX + shortCode, value, ttl);
        } catch (Exception e) {
            log.warn("Redis SET NX failed for {}: {}", shortCode, e.getMessage());
        }
    }

    private static Duration jittered(Duration base) {
        double factor = 0.9 + ThreadLocalRandom.current().nextDouble() * 0.2;
        return Duration.ofMillis((long) (base.toMillis() * factor));
    }
}
