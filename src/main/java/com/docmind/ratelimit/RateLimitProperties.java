package com.docmind.ratelimit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Token bucket for {@code POST /api/ask}: {@code capacity} is the burst size,
 * {@code refillTokens} are added every {@code refillPeriod}, per API key.
 */
@Validated
@ConfigurationProperties("docmind.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30") @Min(1) @Max(10_000) int capacity,
        @DefaultValue("30") @Min(1) @Max(10_000) int refillTokens,
        @DefaultValue("1m") @NotNull Duration refillPeriod) {
}
