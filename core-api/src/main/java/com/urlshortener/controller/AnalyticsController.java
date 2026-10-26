package com.urlshortener.controller;

import com.urlshortener.dto.GlobalStatsResponse;
import com.urlshortener.dto.UrlAnalyticsResponse;
import com.urlshortener.dto.UrlInfoResponse;
import com.urlshortener.service.AnalyticsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsQueryService analyticsQueryService;

    /** GET /api/v1/urls/{shortCode} */
    @GetMapping("/urls/{shortCode}")
    public ResponseEntity<UrlInfoResponse> getUrlInfo(@PathVariable String shortCode) {
        return ResponseEntity.ok(analyticsQueryService.getUrlInfo(shortCode));
    }

    /** GET /api/v1/urls/{shortCode}/analytics?from=...&to=...&groupBy=day */
    @GetMapping("/urls/{shortCode}/analytics")
    public ResponseEntity<UrlAnalyticsResponse> getAnalytics(
            @PathVariable String shortCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to) {

        ZonedDateTime effectiveFrom = from != null ? from : ZonedDateTime.now().minusDays(30);
        ZonedDateTime effectiveTo   = to   != null ? to   : ZonedDateTime.now();

        return ResponseEntity.ok(analyticsQueryService.getAnalytics(shortCode, effectiveFrom, effectiveTo));
    }

    /** GET /api/v1/stats */
    @GetMapping("/stats")
    public ResponseEntity<GlobalStatsResponse> getGlobalStats() {
        return ResponseEntity.ok(analyticsQueryService.getGlobalStats());
    }
}
