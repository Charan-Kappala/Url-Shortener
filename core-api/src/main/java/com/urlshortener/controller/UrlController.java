package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.dto.CreateUrlResponse;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    /** Required in the X-Admin-Key header to delete links. Blank (the default) disables deletion. */
    @Value("${app.admin-api-key:}")
    private String adminApiKey;

    @PostMapping
    public ResponseEntity<CreateUrlResponse> createShortUrl(@Valid @RequestBody CreateUrlRequest request,
                                                            HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        CreateUrlResponse response = urlService.createShortUrl(request, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Soft-deletes a link (is_active = false); it stops redirecting immediately. Admin only. */
    @DeleteMapping("/{shortCode}")
    public ResponseEntity<Void> deleteShortUrl(@PathVariable String shortCode,
                                               @RequestHeader(value = "X-Admin-Key", required = false) String key) {
        if (!isAdmin(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        urlService.deactivate(shortCode);
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(String key) {
        // Constant-time comparison, so response timing does not reveal how much of a guess was right.
        return !adminApiKey.isBlank() && key != null
                && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), adminApiKey.getBytes(StandardCharsets.UTF_8));
    }
}
