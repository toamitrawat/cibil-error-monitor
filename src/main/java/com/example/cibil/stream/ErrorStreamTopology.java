package com.example.cibil.stream;

import com.example.cibil.service.ErrorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.StoreBuilder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Component
@RequiredArgsConstructor
public class ErrorStreamTopology {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ErrorService errorService;

    @Value("${app.kafka.error-topic:Error-topic}")
    private String errorTopic;

    @Value("${app.db.retry.max-attempts:3}")
    private int dbRetryMaxAttempts;

    @Value("${app.db.retry.backoff-ms:500}")
    private long dbRetryBackoffMs;

    private static final Logger logger = LogManager.getLogger(ErrorStreamTopology.class);

    public void build(StreamsBuilder builder) {
        logger.info("Building error stream topology (custom minute aggregation)");
        KStream<String, String> source = builder.stream(errorTopic, Consumed.with(Serdes.String(), Serdes.String()));

        // Repartition to a SINGLE partition so only one active task across the cluster performs
        // minute aggregation + DB writes (prevents duplicate rows when scaling pods)
        KStream<String, String> singlePartition = source
            .selectKey((k, v) -> "ALL")
            .repartition(Repartitioned.
                <String, String>as("error-monitor-single-part")
                .withKeySerde(Serdes.String())
                .withValueSerde(Serdes.String())
                .withNumberOfPartitions(1));

        // State store to hold per-minute aggregate (key = minuteStartEpochMillis as String)
        final String STORE_NAME = "minute-aggregate-store";
        StoreBuilder<KeyValueStore<String, CountAggregate>> storeBuilder = Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(STORE_NAME),
            Serdes.String(),
            new JsonSerde<>(CountAggregate.class)
        );
        builder.addStateStore(storeBuilder);

    singlePartition
            .mapValues(v -> {
                try {
                    JsonNode root = mapper.readTree(v);
                    JsonNode statusNode = root.path("Request").path("status");
                    String status = statusNode.isMissingNode() ? "UNKNOWN" : statusNode.asText();
                    logger.debug("Parsed status: {}", status);
                    return status;
                } catch (Exception e) {
                    logger.error("Failed to parse message: {}", v, e);
                    return "PARSE_ERROR";
                }
            })
            .process(() -> new MinuteAggregationProcessor(errorService, logger, STORE_NAME, dbRetryMaxAttempts, dbRetryBackoffMs), STORE_NAME);
    }

    /**
     * Transformer that accumulates counts per wall-clock minute and flushes (persisting to DB)
     * once the minute is complete via punctuation (WALL_CLOCK_TIME). Ensures exactly one
     * DB insert per minute regardless of message count.
     */
    static class MinuteAggregationProcessor implements Processor<String,String, Void, Void> {
        private final ErrorService errorService;
        private final Logger log;
        private final String storeName;
        private final int maxAttempts;
        private final long backoffMs;
        private KeyValueStore<String, CountAggregate> store;
        private static final long ONE_MINUTE_MS = 60_000L;
    private long lastFlushedMinuteStart = -1L; // tracks last minute start we persisted

        MinuteAggregationProcessor(ErrorService errorService, Logger log, String storeName, int maxAttempts, long backoffMs) {
            this.errorService = errorService;
            this.log = log;
            this.storeName = storeName;
            this.maxAttempts = maxAttempts;
            this.backoffMs = backoffMs;
        }

        @Override
    public void init(ProcessorContext<Void, Void> context) {
            @SuppressWarnings("unchecked")
            KeyValueStore<String, CountAggregate> kv = (KeyValueStore<String, CountAggregate>) context.getStateStore(storeName);
            this.store = kv;
            context.schedule(Duration.ofSeconds(5), PunctuationType.WALL_CLOCK_TIME, this::flushCompleted);
        }

        @Override
        public void process(Record<String,String> record) {
            long eventTs = record.timestamp();
            long minuteStart = eventTs - (eventTs % ONE_MINUTE_MS);
            String minuteKey = Long.toString(minuteStart);
            CountAggregate agg = store.get(minuteKey);
            if (agg == null) agg = new CountAggregate();
            agg.total++;
            if ("Failure".equalsIgnoreCase(record.value())) agg.errors++;
            store.put(minuteKey, agg);
            log.trace("Accumulated minuteKey={}, total={}, errors={}", minuteKey, agg.total, agg.errors);
        }

        private void flushCompleted(long nowTs) {
            long currentMinuteStart = nowTs - (nowTs % ONE_MINUTE_MS);
            // We'll collect minutes with data to flush first (actual counts)
            java.util.List<Long> toFlush = new java.util.ArrayList<>();
            try (KeyValueIterator<String, CountAggregate> iter = store.all()) {
                while (iter.hasNext()) {
                    KeyValue<String, CountAggregate> entry = iter.next();
                    long bucketStart = Long.parseLong(entry.key);
                    if (bucketStart < currentMinuteStart) toFlush.add(bucketStart);
                }
            }
            if (toFlush.isEmpty()) {
                // Possibly emit zero rows for gaps after prior data
                emitZeroGaps(currentMinuteStart);
                return;
            }
            java.util.Collections.sort(toFlush);
            for (Long bucketStart : toFlush) {
                CountAggregate agg = store.get(Long.toString(bucketStart));
                if (agg == null) continue; // defensive
                long bucketEnd = bucketStart + ONE_MINUTE_MS;
                double errorRate = agg.total > 0 ? (agg.errors * 100.0 / agg.total) : 0.0;
                log.info("Flushing minute {} - {} ms: total={}, errors={}, errorRate={}", bucketStart, bucketEnd, agg.total, agg.errors, errorRate);
                runWithRetry("saveStats", () -> errorService.saveStats(java.time.Instant.ofEpochMilli(bucketStart), java.time.Instant.ofEpochMilli(bucketEnd), agg.total, agg.errors, errorRate));
                // evaluation now uses last 5 persisted entries' averages; retry separately so a breaker failure doesn't duplicate stats
                runWithRetry("evaluateCircuitBreaker", () -> errorService.evaluateCircuitBreaker(errorRate, agg.total));
                store.delete(Long.toString(bucketStart));
                lastFlushedMinuteStart = bucketStart;
            }
            // After flushing real data minutes, emit zero gaps up to (but excluding) current active minute
            emitZeroGaps(currentMinuteStart);
        }

        private void emitZeroGaps(long currentMinuteStart) {
            long lastComplete = currentMinuteStart - ONE_MINUTE_MS; // last fully completed minute
            if (lastComplete < 0) return;
            long nextMinute;
            if (lastFlushedMinuteStart < 0) {
                // First run with no prior data persisted: start at lastComplete only
                nextMinute = lastComplete;
            } else {
                nextMinute = lastFlushedMinuteStart + ONE_MINUTE_MS;
            }
            while (nextMinute <= lastComplete) {
                long nm = nextMinute; // effectively final copy for lambda
                long bucketEnd = nm + ONE_MINUTE_MS;
                log.info("Flushing empty minute {} - {} ms: total=0, errors=0, errorRate=0.0", nm, bucketEnd);
                runWithRetry("saveStatsZeroMinute", () -> errorService.saveStats(java.time.Instant.ofEpochMilli(nm), java.time.Instant.ofEpochMilli(bucketEnd), 0L, 0L, 0.0));
                lastFlushedMinuteStart = nm;
                nextMinute += ONE_MINUTE_MS;
            }
        }

        private void runWithRetry(String label, Runnable action) {
            int attempt = 1;
            while (true) {
                try {
                    action.run();
                    if (attempt > 1) {
                        log.warn("DB action '{}' succeeded on attempt {}", label, attempt);
                    }
                    return;
                } catch (Exception e) {
                    if (attempt >= maxAttempts) {
                        log.error("DB action '{}' failed after {} attempts; giving up. Error: {}", label, attempt, e.getMessage(), e);
                        return; // swallow to prevent app crash
                    }
                    long sleep = backoffMs * attempt; // simple linear backoff
                    log.warn("DB action '{}' failed on attempt {}/{}; retrying in {} ms. Error: {}", label, attempt, maxAttempts, sleep, e.getMessage());
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted for action '{}'", label, ie);
                        return;
                    }
                    attempt++;
                }
            }
        }

        @Override
        public void close() {
            flushCompleted(System.currentTimeMillis());
        }
    }
}
