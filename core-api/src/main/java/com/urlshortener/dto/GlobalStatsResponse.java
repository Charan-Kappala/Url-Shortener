package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GlobalStatsResponse {
    private Long totalUrlsCreated;
    private Long totalClicks;
    private Long urlsCreatedToday;
}
