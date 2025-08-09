package com.example.cibil.config;

import com.example.cibil.stream.ErrorStreamTopology;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.KafkaStreamsCustomizer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Configuration
@EnableKafkaStreams
public class KafkaStreamsConfig {

    private static final Logger logger = LogManager.getLogger(KafkaStreamsConfig.class);

    // Spring will create the default StreamsBuilder based on spring.kafka.* properties.
    // We just hook in our topology definition.
    @Bean
    public Object buildErrorTopology(StreamsBuilder builder, ErrorStreamTopology errorStreamTopology) {
        errorStreamTopology.build(builder);
        // Return a non-null bean so Spring registers it; topology is built via side-effect.
        return new Object();
    }

    @Bean
    public KafkaStreamsCustomizer kafkaStreamsCustomizer() {
        return (KafkaStreams streams) -> {
            streams.setStateListener((newState, oldState) ->
                logger.info("Kafka Streams state change: {} -> {}", oldState, newState));
            streams.setUncaughtExceptionHandler(ex -> {
                logger.error("Uncaught exception in Kafka Streams", ex);
                return org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_APPLICATION;
            });
        };
    }
}
