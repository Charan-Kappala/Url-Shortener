package com.urlshortener.dto;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

@Data
@Builder
public class CreateUrlResponse {
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private ZonedDateTime expiresAt;
    private ZonedDateTime createdAt;
}
