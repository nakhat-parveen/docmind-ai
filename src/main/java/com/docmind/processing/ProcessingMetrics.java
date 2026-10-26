package com.docmind.processing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Custom metrics for background processing. Exposed under /actuator/prometheus as
 * docmind_documents_processed_total, docmind_documents_processing_duration_seconds
 * and docmind_kafka_listener_errors_total.
 */
@Component
public class ProcessingMetrics {

    private final MeterRegistry registry;
    private final Counter ready;
    private final Counter failed;

    public ProcessingMetrics(MeterRegistry registry) {
        this.registry = registry;
        // registered up front so dashboards show 0 instead of "no data" after a restart
        this.ready = processedCounter("ready");
        this.failed = processedCounter("failed");
    }

    public Timer.Sample startProcessing() {
        return Timer.start(registry);
    }

    /**
     * Records how long one processing attempt took, successful or not.
     */
    public void stopProcessing(Timer.Sample sample, boolean success) {
        sample.stop(Timer.builder("docmind.documents.processing.duration")
                .description("Time spent extracting, chunking and embedding one document")
                .tag("outcome", success ? "success" : "error")
                .register(registry));
    }

    public void documentReady() {
        ready.increment();
    }

    public void documentFailed() {
        failed.increment();
    }

    /**
     * Counts every failed delivery of a Kafka message, including the ones that are retried.
     */
    public void listenerError(Throwable error) {
        Counter.builder("docmind.kafka.listener.errors")
                .description("Failed attempts to process a Kafka message")
                .tag("exception", error.getClass().getSimpleName())
                .register(registry)
                .increment();
    }

    private Counter processedCounter(String outcome) {
        return Counter.builder("docmind.documents.processed")
                .description("Documents that finished background processing")
                .tag("outcome", outcome)
                .register(registry);
    }
}
