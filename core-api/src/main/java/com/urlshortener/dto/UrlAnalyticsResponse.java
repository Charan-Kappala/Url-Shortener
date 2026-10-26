package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UrlAnalyticsResponse {
    private String shortCode;
    private Long totalClicks;
    private Long uniqueDays;
    private List<RefererCount> topReferers;
    private List<DeviceCount> topDevices;
    private List<DayCount> clicksByDay;

    @Data
    @Builder
    public static class RefererCount {
        private String referer;
        private Long count;
    }

    @Data
    @Builder
    public static class DeviceCount {
        private String device;
        private Long count;
    }

    @Data
    @Builder
    public static class DayCount {
        private String date;
        private Long count;
    }
}
