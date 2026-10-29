package com.docmind.outbox;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * How often the relay looks for unsent events, how many it sends per run, how long
 * it waits for Kafka to confirm one event, and how long sent events are kept.
 */
@Validated
@ConfigurationProperties("docmind.outbox")
public record OutboxProperties(
        @DefaultValue("1s") @NotNull Duration pollInterval,
        @DefaultValue("50") @Min(1) @Max(500) int batchSize,
        @DefaultValue("15s") @NotNull Duration sendTimeout,
        @DefaultValue("7d") @NotNull Duration retention) {
}
