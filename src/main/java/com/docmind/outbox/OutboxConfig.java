package com.docmind.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class OutboxConfig {

    /**
     * Exposed as docmind_outbox_pending. A value that keeps growing means the relay
     * cannot reach Kafka.
     */
    @Bean
    MeterBinder outboxPendingGauge(OutboxEventRepository repository) {
        return registry -> Gauge.builder("docmind.outbox.pending", repository, OutboxEventRepository::countBySentAtIsNull)
                .description("Upload events that are stored but not yet sent to Kafka")
                .register(registry);
    }
}
