package com.example.cibil.stream;

import com.example.cibil.service.ErrorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ErrorStreamTopology {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ErrorService errorService;

    public ErrorStreamTopology(ErrorService errorService) {
        this.errorService = errorService;
    }

    public void build(StreamsBuilder builder) {
        KStream<String, String> source = builder.stream("Error-topic", Consumed.with(Serdes.String(), Serdes.String()));

        KStream<String, String> statuses = source
            .mapValues(v -> {
                try {
                    JsonNode root = mapper.readTree(v);
                    JsonNode statusNode = root.path("Request").path("status");
                    return statusNode.isMissingNode() ? "UNKNOWN" : statusNode.asText();
                } catch (Exception e) {
                    return "PARSE_ERROR";
                }
            });

        // Global grouping - single key
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
                    return agg;
                },
                Materialized.with(Serdes.String(), new JsonSerde<>(CountAggregate.class))
            )
            .toStream()
            .foreach((windowedKey, agg) -> {
                double errorRate = agg.total > 0 ? (agg.errors * 100.0 / agg.total) : 0.0;

                // Persist stats
        errorService.saveStats(windowedKey.window().startTime(),
            windowedKey.window().endTime(), agg.total, agg.errors, errorRate);

                // Evaluate circuit breaker
                errorService.evaluateCircuitBreaker(errorRate);
            });
    }
}
