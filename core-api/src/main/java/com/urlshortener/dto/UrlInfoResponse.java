package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
@Builder
public class UrlInfoResponse {
    private String shortCode;
    private String originalUrl;
    private Long clickCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
    private Boolean isActive;
}
