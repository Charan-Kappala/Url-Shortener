package com.urlshortener.service;

import com.urlshortener.dto.GlobalStatsResponse;
import com.urlshortener.dto.UrlAnalyticsResponse;
import com.urlshortener.dto.UrlInfoResponse;
import com.urlshortener.exception.ShortCodeNotFoundException;
import com.urlshortener.model.UrlMapping;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;

    @Transactional(readOnly = true)
    public UrlInfoResponse getUrlInfo(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException("Short code not found: " + shortCode));
        return UrlInfoResponse.builder()
                .shortCode(mapping.getShortCode())
                .originalUrl(mapping.getOriginalUrl())
                .clickCount(mapping.getClickCount())
                .createdAt(mapping.getCreatedAt())
                .expiresAt(mapping.getExpiresAt())
                .isActive(mapping.getIsActive())
                .build();
    }

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(String shortCode, ZonedDateTime from, ZonedDateTime to) {
        if (!urlMappingRepository.existsByShortCode(shortCode)) {
            throw new ShortCodeNotFoundException("Short code not found: " + shortCode);
        }

        Long totalClicks = clickEventRepository.countByShortCode(shortCode);
        Long uniqueDays   = clickEventRepository.countUniqueDaysByShortCode(shortCode);

        List<Object[]> refRows    = clickEventRepository.findTopReferersByShortCode(shortCode);
        List<Object[]> deviceRows = clickEventRepository.findTopDevicesByShortCode(shortCode);
        List<Object[]> dayRows    = clickEventRepository.findClicksByDay(shortCode, from, to);

        List<UrlAnalyticsResponse.RefererCount> topReferers = refRows.stream()
                .map(r -> UrlAnalyticsResponse.RefererCount.builder()
                        .referer((String) r[0]).count((Long) r[1]).build())
                .toList();

        List<UrlAnalyticsResponse.DeviceCount> topDevices = deviceRows.stream()
                .map(r -> UrlAnalyticsResponse.DeviceCount.builder()
                        .device((String) r[0]).count((Long) r[1]).build())
                .toList();

        List<UrlAnalyticsResponse.DayCount> clicksByDay = dayRows.stream()
                .map(r -> UrlAnalyticsResponse.DayCount.builder()
                        .date(r[0].toString()).count((Long) r[1]).build())
                .toList();

        return UrlAnalyticsResponse.builder()
                .shortCode(shortCode)
                .totalClicks(totalClicks)
                .uniqueDays(uniqueDays)
                .topReferers(topReferers)
                .topDevices(topDevices)
                .clicksByDay(clicksByDay)
                .build();
    }

    @Transactional(readOnly = true)
    public GlobalStatsResponse getGlobalStats() {
        long totalUrls  = urlMappingRepository.count();
        long totalClicks = urlMappingRepository.findAll().stream()
                .mapToLong(m -> m.getClickCount() == null ? 0 : m.getClickCount()).sum();
        ZonedDateTime startOfDay = ZonedDateTime.now().toLocalDate().atStartOfDay(ZonedDateTime.now().getZone());
        long urlsToday = urlMappingRepository.findAll().stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(startOfDay)).count();

        return GlobalStatsResponse.builder()
                .totalUrlsCreated(totalUrls)
                .totalClicks(totalClicks)
                .urlsCreatedToday(urlsToday)
                .build();
    }
}
