package com.example.cibil.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class JsonSerde<T> implements Serde<T> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Class<T> clazz;

    public JsonSerde(Class<T> clazz) { this.clazz = clazz; }

    @Override
    public Serializer<T> serializer() {
        return (topic, data) -> {
            try {
                return mapper.writeValueAsBytes(data);
            } catch (Exception e) {
                return null;
            }
        };
    }

    @Override
    public Deserializer<T> deserializer() {
        return (topic, bytes) -> {
            try {
                if (bytes == null) return null;
                return mapper.readValue(bytes, clazz);
            } catch (Exception e) {
                return null;
            }
        };
    }

    @Override public void configure(Map<String, ?> configs, boolean isKey) {}
    @Override public void close() {}
}
