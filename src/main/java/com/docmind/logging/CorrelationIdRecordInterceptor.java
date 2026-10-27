package com.docmind.logging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Restores the correlation id from the Kafka record header before a listener runs,
 * so the consumer, the retries and the error handler log with the same id as the
 * upload request. Spring Boot adds this bean to the listener container automatically.
 */
@Component
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(CorrelationId.HEADER);
        String value = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
        MDC.put(CorrelationId.MDC_KEY, CorrelationId.fromOrNew(value));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(CorrelationId.MDC_KEY);
    }
}
