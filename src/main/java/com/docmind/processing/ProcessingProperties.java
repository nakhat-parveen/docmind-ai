package com.docmind.processing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Retry settings for background processing. {@code maxAttempts} includes the first try.
 */
@Validated
@ConfigurationProperties("docmind.processing")
public record ProcessingProperties(
        @DefaultValue("3") @Min(1) int maxAttempts,
        @DefaultValue("2s") @NotNull Duration retryInterval) {
}
