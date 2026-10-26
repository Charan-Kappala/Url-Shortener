package com.urlshortener.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.model.ClickEventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Takes click recording off the redirect path.
 *
 * {@link #publish} only offers the event to a bounded in-memory buffer: it never blocks, never touches the
 * network and never throws. Background sender threads drain the buffer and send to SQS in batches of up to
 * 10 messages (the SQS batch limit). The AWS SDK already retries transient send errors with backoff.
 *
 * When SQS is down or too slow, the buffer fills and new events are dropped and counted
 * ({@code clicks.dropped}) instead of slowing redirects down: the redirect is the product, the click count is
 * analytics. The buffer rides out short outages (about capacity / click rate seconds) before dropping starts.
 */
@Slf4j
@Service
public class ClickEventPublisher {

    private static final int SQS_MAX_BATCH = 10;

    private final SqsAsyncClient sqs;
    private final ObjectMapper json;
    private final String queueUrl;
    private final BlockingQueue<ClickEventMessage> buffer;
    private final ExecutorService senders;
    private final Counter published;
    private final Counter droppedBufferFull;
    private final Counter droppedSendFailed;
    private volatile boolean running = true;

    public ClickEventPublisher(SqsAsyncClient sqs,
                               ObjectMapper json,
                               MeterRegistry metrics,
                               @Value("${aws.sqs.click-events-queue-url}") String queueUrl,
                               @Value("${app.clicks.buffer-capacity:10000}") int bufferCapacity,
                               @Value("${app.clicks.sender-threads:2}") int senderThreads) {
        this.sqs = sqs;
        this.json = json;
        this.queueUrl = queueUrl;
        this.buffer = new ArrayBlockingQueue<>(bufferCapacity);
        this.published = metrics.counter("clicks.published");
        this.droppedBufferFull = metrics.counter("clicks.dropped", "reason", "buffer_full");
        this.droppedSendFailed = metrics.counter("clicks.dropped", "reason", "send_failed");
        metrics.gaugeCollectionSize("clicks.buffered", List.of(), buffer);

        this.senders = Executors.newFixedThreadPool(senderThreads);
        for (int i = 0; i < senderThreads; i++) {
            senders.submit(this::sendLoop);
        }
    }

    /** Called on every redirect. Returns immediately. */
    public void publish(ClickEventMessage event) {
        if (!buffer.offer(event)) {
            droppedBufferFull.increment();
        }
    }

    private void sendLoop() {
        List<ClickEventMessage> batch = new ArrayList<>(SQS_MAX_BATCH);
        while (running || !buffer.isEmpty()) {
            try {
                ClickEventMessage first = buffer.poll(500, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                buffer.drainTo(batch, SQS_MAX_BATCH - 1);
                send(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                batch.clear();
            }
        }
    }

    private void send(List<ClickEventMessage> batch) {
        try {
            List<SendMessageBatchRequestEntry> entries = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                entries.add(SendMessageBatchRequestEntry.builder()
                        .id(Integer.toString(i))
                        .messageBody(json.writeValueAsString(batch.get(i)))
                        .build());
            }
            SendMessageBatchResponse response = sqs
                    .sendMessageBatch(r -> r.queueUrl(queueUrl).entries(entries))
                    .get(10, TimeUnit.SECONDS);
            published.increment(response.successful().size());
            if (!response.failed().isEmpty()) {
                droppedSendFailed.increment(response.failed().size());
                log.warn("SQS rejected {} of {} click events", response.failed().size(), batch.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            droppedSendFailed.increment(batch.size());
        } catch (Exception e) {
            droppedSendFailed.increment(batch.size());
            log.warn("Dropping {} click events, SQS send failed: {}", batch.size(), e.toString());
        }
    }

    /** On shutdown, stop accepting work and give senders a few seconds to flush what is buffered. */
    @PreDestroy
    void shutdown() throws InterruptedException {
        running = false;
        senders.shutdown();
        senders.awaitTermination(10, TimeUnit.SECONDS);
    }
}
