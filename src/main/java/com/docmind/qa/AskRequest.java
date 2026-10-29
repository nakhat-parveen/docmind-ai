package com.docmind.qa;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AskRequest(
        @Schema(description = "The question in plain language", example = "How many vacation days do new employees get?")
        @NotBlank @Size(max = 2000) String question,
        @Schema(description = "How many chunks to use as context. Defaults to docmind.retrieval.default-top-k")
        @Min(1) @Max(20) Integer topK,
        @Schema(description = "Only use these documents. Leave out to search all documents")
        @Size(max = 50) List<UUID> documentIds) {
}
