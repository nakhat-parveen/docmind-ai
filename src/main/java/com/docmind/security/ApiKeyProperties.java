package com.docmind.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties("docmind.security")
public record ApiKeyProperties(
        @DefaultValue("X-API-Key") @NotBlank String headerName,
        @NotEmpty List<@NotBlank String> apiKeys) {
}
