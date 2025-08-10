package com.example.cibil.stream.handler;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A simple DeserializationExceptionHandler that logs the failing record metadata and continues.
 * This prevents the application from shutting down due to malformed messages.
 */
public class LoggingContinueDeserializationExceptionHandler implements DeserializationExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoggingContinueDeserializationExceptionHandler.class);

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op
    }

    @Override
    public DeserializationHandlerResponse handle(ProcessorContext context, ConsumerRecord<byte[], byte[]> record, Exception exception) {
        try {
            Headers headers = record == null ? null : record.headers();
            logger.error("Deserialization error on topic={} partition={} offset={} headers={} : {}", 
                record == null ? null : record.topic(),
                record == null ? null : record.partition(),
                record == null ? null : record.offset(),
                headers,
                exception.toString());
        } catch (Exception logEx) {
            logger.error("Failed while logging deserialization exception", logEx);
        }
        return DeserializationHandlerResponse.CONTINUE; // skip the bad record
    }
}
