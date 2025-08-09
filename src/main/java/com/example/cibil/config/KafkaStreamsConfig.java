package com.example.cibil.config;

import com.example.cibil.stream.ErrorStreamTopology;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaStreamsConfig {
    private final ErrorStreamTopology topology;

    public KafkaStreamsConfig(ErrorStreamTopology topology) {
        this.topology = topology;
    }

    @Bean
    public StreamsBuilder streamsBuilder() {
        StreamsBuilder builder = new StreamsBuilder();
        topology.build(builder);
        return builder;
    }
}
