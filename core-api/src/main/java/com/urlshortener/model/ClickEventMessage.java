package com.urlshortener.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** One redirect, as published to the click-events queue. */
@Data
@Builder
public class ClickEventMessage {
    /** Assigned once per redirect; the consumer uses it to ignore redelivered copies. */
    private UUID eventId;
    private String shortCode;
    private Instant clickedAt;
    private String userAgent;
    private String referer;
}
