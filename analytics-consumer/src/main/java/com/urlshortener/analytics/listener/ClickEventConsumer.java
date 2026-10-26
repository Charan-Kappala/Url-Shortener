package com.urlshortener.analytics.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.analytics.model.ClickEventMessage;
import com.urlshortener.analytics.service.ClickEventWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Polls the click-events queue and writes events to Postgres in batches.
 *
 * SQS returns at most 10 messages per receive, so a poller keeps receiving until it holds
 * {@code batch-size} events or the oldest has waited {@code max-wait-ms}; then it writes them in one
 * transaction. Bigger batches mean fewer transactions and fewer click_count updates for popular links.
 *
 * Order per batch: receive (several times) -> write to Postgres (one transaction) -> delete from SQS.
 * A message is deleted only after its event is committed, so a crash at any point loses nothing:
 * undeleted messages become visible again after the queue's visibility timeout and are retried, and
 * ClickEventWriter ignores events it has already stored. A message that cannot be parsed is never deleted;
 * after 5 receives SQS moves it to the dead-letter queue.
 */
@Slf4j
@Component
public class ClickEventConsumer implements SmartLifecycle {

    private final SqsAsyncClient sqs;
    private final ObjectMapper json;
    private final ClickEventWriter writer;
    private final String queueUrl;
    private final int threads;
    private final int batchSize;
    private final long maxWaitMillis;
    private ExecutorService pollers;
    private volatile boolean running;

    public ClickEventConsumer(SqsAsyncClient sqs, ObjectMapper json, ClickEventWriter writer,
                              @Value("${aws.sqs.click-events-queue-url}") String queueUrl,
                              @Value("${app.consumer.threads:2}") int threads,
                              @Value("${app.consumer.batch-size:200}") int batchSize,
                              @Value("${app.consumer.max-wait-ms:1000}") long maxWaitMillis) {
        this.sqs = sqs;
        this.json = json;
        this.writer = writer;
        this.queueUrl = queueUrl;
        this.threads = threads;
        this.batchSize = batchSize;
        this.maxWaitMillis = maxWaitMillis;
    }

    @Override
    public void start() {
        running = true;
        pollers = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pollers.submit(this::pollLoop);
        }
        log.info("Consuming click events from {} with {} poller(s)", queueUrl, threads);
    }

    @Override
    public void stop() {
        running = false;
        pollers.shutdown();
        try {
            pollers.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollLoop() {
        List<Message> pending = new ArrayList<>();
        long flushAt = 0;
        while (running) {
            try {
                // Long-poll while idle; once a batch has started, only wait briefly for more.
                int waitSeconds = pending.isEmpty() ? 5 : 1;
                List<Message> received = sqs.receiveMessage(r -> r.queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(waitSeconds)).get().messages();
                if (pending.isEmpty() && !received.isEmpty()) {
                    flushAt = System.currentTimeMillis() + maxWaitMillis;
                }
                pending.addAll(received);
                if (!pending.isEmpty() && (pending.size() >= batchSize || System.currentTimeMillis() >= flushAt)) {
                    process(pending);
                    pending.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Nothing was deleted, so SQS redelivers these messages after the visibility timeout.
                log.warn("Click event batch of {} failed, it will be redelivered: {}", pending.size(), e.toString());
                pending.clear();
                sleepQuietly(1000);
            }
        }
        // Stopping: messages still pending are not deleted and will be redelivered to another poller.
    }

    private void process(List<Message> messages) throws Exception {
        List<ClickEventMessage> events = new ArrayList<>();
        List<DeleteMessageBatchRequestEntry> processed = new ArrayList<>();
        for (Message m : messages) {
            try {
                events.add(json.readValue(m.body(), ClickEventMessage.class));
                processed.add(DeleteMessageBatchRequestEntry.builder()
                        .id(m.messageId()).receiptHandle(m.receiptHandle()).build());
            } catch (Exception e) {
                log.error("Unparseable click event {} left for the DLQ: {}", m.messageId(), e.getMessage());
            }
        }
        if (events.isEmpty()) return;

        int newEvents = writer.write(events);
        for (int i = 0; i < processed.size(); i += 10) { // SQS deletes at most 10 per call
            List<DeleteMessageBatchRequestEntry> chunk = processed.subList(i, Math.min(i + 10, processed.size()));
            sqs.deleteMessageBatch(r -> r.queueUrl(queueUrl).entries(chunk)).get();
        }
        log.debug("Batch of {}: {} new, {} already recorded", events.size(), newEvents, events.size() - newEvents);
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
