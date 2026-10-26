package com.urlshortener.analytics.service;

import com.urlshortener.analytics.model.ClickEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes one batch of click events in a single transaction, idempotently.
 *
 * 1. Insert the raw events with ON CONFLICT (event_id) DO NOTHING. An event that was already stored
 *    (SQS delivered it again) inserts 0 rows, so the per-row update count tells new events from repeats.
 * 2. Add the new events to url_mappings.click_count: one UPDATE per short code per batch, not one per click.
 *
 * Both steps commit together. If the process dies before the commit, nothing is written and SQS redelivers
 * the batch; if it dies after the commit but before the messages are deleted, the redelivered events hit
 * the ON CONFLICT and are not counted again.
 */
@Service
@RequiredArgsConstructor
public class ClickEventWriter {

    private final JdbcTemplate jdbc;
    private final DeviceDetectionService deviceDetection;

    /** Returns how many events in the batch were new (not seen before). */
    @Transactional
    public int write(List<ClickEventMessage> events) {
        int[] inserted = jdbc.batchUpdate("""
                INSERT INTO click_events (event_id, short_code, clicked_at, user_agent, referer, device_type)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING""",
                events, events.size(), (ps, e) -> {
                    ps.setObject(1, e.getEventId());
                    ps.setString(2, e.getShortCode());
                    ps.setTimestamp(3, Timestamp.from(e.getClickedAt()));
                    ps.setString(4, e.getUserAgent());
                    ps.setString(5, e.getReferer());
                    ps.setString(6, deviceDetection.detectDeviceType(e.getUserAgent()));
                })[0];

        // Sorted by short code so concurrent batches lock url_mappings rows in the same order (no deadlocks).
        Map<String, Integer> newClicksPerCode = new TreeMap<>();
        int newEvents = 0;
        for (int i = 0; i < events.size(); i++) {
            if (inserted[i] == 1) {
                newClicksPerCode.merge(events.get(i).getShortCode(), 1, Integer::sum);
                newEvents++;
            }
        }

        jdbc.batchUpdate("UPDATE url_mappings SET click_count = click_count + ? WHERE short_code = ?",
                List.copyOf(newClicksPerCode.entrySet()), newClicksPerCode.size(), (ps, entry) -> {
                    ps.setInt(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                });
        return newEvents;
    }
}
