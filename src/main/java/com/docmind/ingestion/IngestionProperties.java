package com.docmind.ingestion;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Chunking settings. Sizes are in tokens, except {@code minChunkSizeChars}.
 */
@Validated
@ConfigurationProperties("docmind.ingestion")
public record IngestionProperties(
        @DefaultValue("400") @Min(50) int chunkSize,
        @DefaultValue("200") @Min(1) int minChunkSizeChars,
        @DefaultValue("5") @Min(1) int minChunkLengthToEmbed,
        @DefaultValue("2000") @Min(1) int maxChunksPerDocument) {
}
