package com.docmind.retrieval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("docmind.retrieval")
public record RetrievalProperties(
        @DefaultValue("5") @Min(1) @Max(20) int defaultTopK,
        @DefaultValue("0.3") @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold) {
}
