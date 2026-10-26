package com.urlshortener.service;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.exception.AliasTakenException;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlMappingRepository urlMappingRepository;
    private final CacheService cacheService;
    private final ShortCodeGenerator shortCodeGenerator;

    private static final int MAX_GENERATED_CODE_ATTEMPTS = 3;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.default-expiry-days:90}")
    private int defaultExpiryDays;

    public CreateUrlResponse createShortUrl(CreateUrlRequest request, String ipAddress) {
        ZonedDateTime createdAt = ZonedDateTime.now();
        ZonedDateTime expiresAt = null;

        if (request.getExpiresInDays() != null) {
            expiresAt = createdAt.plusDays(request.getExpiresInDays());
        } else if (defaultExpiryDays > 0) {
            expiresAt = createdAt.plusDays(defaultExpiryDays);
        }

        String customCode = request.getCustomCode();
        UrlMapping mapping;
        if (customCode != null && !customCode.isEmpty()) {
            // No "exists?" check first: two concurrent requests could both pass it.
            // The UNIQUE constraint on short_code decides who wins; the loser gets 409.
            try {
                mapping = insert(customCode, request, ipAddress, createdAt, expiresAt);
            } catch (DataIntegrityViolationException e) {
                if (isUniqueViolation(e)) throw new AliasTakenException(customCode);
                throw e;
            }
        } else {
            mapping = insertWithGeneratedCode(request, ipAddress, createdAt, expiresAt);
        }

        return CreateUrlResponse.builder()
            .shortCode(mapping.getShortCode())
            .shortUrl(baseUrl + "/" + mapping.getShortCode())
            .originalUrl(mapping.getOriginalUrl())
            .createdAt(createdAt)
            .expiresAt(expiresAt)
            .build();
    }

    /**
     * Generated codes never repeat, but a user may already have taken one of them as a custom alias.
     * Then the insert hits the unique constraint and the next generated code is used.
     */
    private UrlMapping insertWithGeneratedCode(CreateUrlRequest request, String ipAddress,
                                               ZonedDateTime createdAt, ZonedDateTime expiresAt) {
        for (int attempt = 1; ; attempt++) {
            try {
                return insert(shortCodeGenerator.nextCode(), request, ipAddress, createdAt, expiresAt);
            } catch (DataIntegrityViolationException e) {
                if (!isUniqueViolation(e) || attempt == MAX_GENERATED_CODE_ATTEMPTS) throw e;
            }
        }
    }

    /** Each insert is its own transaction, so a unique violation does not poison a surrounding one. */
    private UrlMapping insert(String shortCode, CreateUrlRequest request, String ipAddress,
                              ZonedDateTime createdAt, ZonedDateTime expiresAt) {
        UrlMapping saved = urlMappingRepository.saveAndFlush(UrlMapping.builder()
            .shortCode(shortCode)
            .originalUrl(request.getOriginalUrl())
            .createdAt(createdAt)
            .expiresAt(expiresAt)
            .isActive(true)
            .createdByIp(ipAddress)
            .clickCount(0L)
            .build());
        // Someone may have requested this code before it existed; clear the NOT_FOUND tombstone.
        cacheService.evict(shortCode);
        return saved;
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getMostSpecificCause() instanceof SQLException sql && "23505".equals(sql.getSQLState());
    }

    /**
     * Deactivates the link in Postgres first, then overwrites its cache entry with a NOT_FOUND tombstone.
     * Readers only fill the cache when the key is absent (SET NX), so a reader that loaded the row just
     * before this commit cannot put the old URL back.
     */
    public void deactivate(String shortCode) {
        if (urlMappingRepository.deactivate(shortCode) == 0) {
            throw new ShortCodeNotFoundException("Short code not found: " + shortCode);
        }
        cacheService.markNotFound(shortCode);
    }

    /**
     * Resolves a code for a redirect. A cache hit (URL or tombstone) needs no Postgres access at all;
     * no transaction is opened here, so a hit does not even borrow a database connection.
     */
    public String getOriginalUrl(String shortCode) {
        String target = cacheService.get(shortCode).orElseGet(() -> loadAndCache(shortCode));

        if (target.equals(CacheService.NOT_FOUND)) {
            throw new ShortCodeNotFoundException("Short code not found");
        }
        if (target.equals(CacheService.GONE)) {
            throw new UrlExpiredException("Short code has expired");
        }
        return target;
    }

    private String loadAndCache(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode).orElse(null);

        if (mapping == null || !mapping.getIsActive()) {
            cacheService.cacheNotFound(shortCode);
            return CacheService.NOT_FOUND;
        }
        if (mapping.getExpiresAt() != null && mapping.getExpiresAt().isBefore(ZonedDateTime.now())) {
            cacheService.cacheGone(shortCode);
            return CacheService.GONE;
        }
        cacheService.cacheLink(shortCode, mapping.getOriginalUrl(), mapping.getExpiresAt());
        return mapping.getOriginalUrl();
    }
}
