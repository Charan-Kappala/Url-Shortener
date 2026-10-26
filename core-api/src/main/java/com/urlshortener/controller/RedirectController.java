package com.urlshortener.controller;

import com.urlshortener.model.ClickEventMessage;
import com.urlshortener.service.ClickEventPublisher;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;
    private final ClickEventPublisher clickEventPublisher;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode,
                                         HttpServletRequest request) {
        String originalUrl = urlService.getOriginalUrl(shortCode);

        clickEventPublisher.publish(ClickEventMessage.builder()
                .eventId(UUID.randomUUID())
                .shortCode(shortCode)
                .clickedAt(Instant.now())
                .userAgent(request.getHeader("User-Agent"))
                .referer(request.getHeader("Referer"))
                .build()); // returns immediately; see ClickEventPublisher

        HttpHeaders headers = new HttpHeaders();
        headers.add("Location", originalUrl);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
