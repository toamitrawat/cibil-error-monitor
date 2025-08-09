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

    private static final Logger logger = LogManager.getLogger(ErrorStreamTopology.class);

    public void build(StreamsBuilder builder) {
        logger.info("Building error stream topology (custom minute aggregation)");
        KStream<String, String> source = builder.stream(errorTopic, Consumed.with(Serdes.String(), Serdes.String()));

        // State store to hold per-minute aggregate (key = minuteStartEpochMillis as String)
        final String STORE_NAME = "minute-aggregate-store";
        StoreBuilder<KeyValueStore<String, CountAggregate>> storeBuilder = Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore(STORE_NAME),
            Serdes.String(),
            new JsonSerde<>(CountAggregate.class)
        );
        builder.addStateStore(storeBuilder);

        source
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
            .process(() -> new MinuteAggregationProcessor(errorService, logger, STORE_NAME), STORE_NAME);
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
        private KeyValueStore<String, CountAggregate> store;
        private ProcessorContext<Void, Void> context;
        private static final long ONE_MINUTE_MS = 60_000L;

        MinuteAggregationProcessor(ErrorService errorService, Logger log, String storeName) {
            this.errorService = errorService;
            this.log = log;
            this.storeName = storeName;
        }

        @Override
        public void init(ProcessorContext<Void, Void> context) {
            this.context = context;
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
            try (KeyValueIterator<String, CountAggregate> iter = store.all()) {
                while (iter.hasNext()) {
                    KeyValue<String, CountAggregate> entry = iter.next();
                    long bucketStart = Long.parseLong(entry.key);
                    if (bucketStart < currentMinuteStart) {
                        long bucketEnd = bucketStart + ONE_MINUTE_MS;
                        CountAggregate agg = entry.value;
                        double errorRate = agg.total > 0 ? (agg.errors * 100.0 / agg.total) : 0.0;
                        log.info("Flushing minute {} - {} ms: total={}, errors={}, errorRate={}", bucketStart, bucketEnd, agg.total, agg.errors, errorRate);
                        errorService.saveStats(java.time.Instant.ofEpochMilli(bucketStart), java.time.Instant.ofEpochMilli(bucketEnd), agg.total, agg.errors, errorRate);
                        errorService.evaluateCircuitBreaker(errorRate, agg.total);
                        store.delete(entry.key);
                    }
                }
            }
        }

        @Override
        public void close() {
            flushCompleted(System.currentTimeMillis());
        }
    }
}
