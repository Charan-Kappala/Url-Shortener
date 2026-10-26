package com.urlshortener.analytics.model;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** A redirect, as published by core-api to the click-events queue. */
@Data
public class ClickEventMessage {
    private UUID eventId;
    private String shortCode;
    private Instant clickedAt;
    private String userAgent;
    private String referer;
}
