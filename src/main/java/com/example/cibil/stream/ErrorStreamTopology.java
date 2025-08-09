package com.example.cibil.stream;

import com.example.cibil.service.ErrorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
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
    logger.info("Building error stream topology");
    KStream<String, String> source = builder.stream(errorTopic, Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> statuses = source
            .mapValues(v -> {
                try {
                    JsonNode root = mapper.readTree(v);
                    JsonNode statusNode = root.path("Request").path("status");
                    logger.debug("Parsed status: {}", statusNode.asText());
                    return statusNode.isMissingNode() ? "UNKNOWN" : statusNode.asText();
                } catch (Exception e) {
                    logger.error("Failed to parse message: {}", v, e);
                    return "PARSE_ERROR";
                }
            });

        // Global grouping - single key
    logger.info("Grouping statuses and starting aggregation");
        statuses
            .map((String k, String v) -> KeyValue.pair("global", v))
            .groupByKey(Grouped.with(Serdes.String(), Serdes.String()))
            // 5 minute window, 0 grace (no late arrivals), advance by 1 minute for sliding effect
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofMinutes(5), Duration.ZERO).advanceBy(Duration.ofMinutes(1)))
            .aggregate(
                CountAggregate::new,
                (key, status, agg) -> {
                    agg.total++;
                    if ("Failure".equalsIgnoreCase(status)) agg.errors++;
                    logger.trace("Aggregating: key={}, status={}, total={}, errors={}", key, status, agg.total, agg.errors);
                    return agg;
                },
                Materialized.with(Serdes.String(), new JsonSerde<>(CountAggregate.class))
            )
            .toStream()
            .foreach((windowedKey, agg) -> {
                double errorRate = agg.total > 0 ? (agg.errors * 100.0 / agg.total) : 0.0;

                // Persist stats
                logger.info("Window {} - {}: total={}, errors={}, errorRate={}",
                    windowedKey.window().startTime(), windowedKey.window().endTime(), agg.total, agg.errors, errorRate);
        errorService.saveStats(windowedKey.window().startTime(),
            windowedKey.window().endTime(), agg.total, agg.errors, errorRate);

                // Evaluate circuit breaker
                logger.debug("Evaluating circuit breaker for window {} - {}", windowedKey.window().startTime(), windowedKey.window().endTime());
                errorService.evaluateCircuitBreaker(errorRate, agg.total);
            });
    }
}
