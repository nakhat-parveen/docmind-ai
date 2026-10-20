package com.docmind.qa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AskRequest(
        @NotBlank @Size(max = 2000) String question,
        @Min(1) @Max(20) Integer topK,
        @Size(max = 50) List<UUID> documentIds) {
}
