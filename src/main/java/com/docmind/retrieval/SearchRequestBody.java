package com.docmind.retrieval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SearchRequestBody(
        @Schema(description = "Text to compare with the indexed chunks", example = "vacation policy")
        @NotBlank @Size(max = 1000) String query,
        @Schema(description = "Maximum number of chunks to return. Defaults to docmind.retrieval.default-top-k")
        @Min(1) @Max(20) Integer topK,
        @Schema(description = "Only search these documents. Leave out to search all documents")
        @Size(max = 50) List<UUID> documentIds) {
}
