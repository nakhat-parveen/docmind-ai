package com.docmind.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";
    private static final String PROBLEM_SCHEMA = "ProblemDetail";
    private static final String PROBLEM_JSON = org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    @Bean
    OpenAPI docMindOpenApi(@Value("${docmind.security.header-name:X-API-Key}") String headerName) {
        return new OpenAPI()
                .info(new Info()
                        .title("DocMind AI API")
                        .version("v1")
                        .description("""
                                Upload documents, search them semantically and ask questions with cited answers.
                                Errors are returned as RFC 9457 problem details (application/problem+json)."""))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(headerName)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }

    /**
     * Adds the responses every API operation can return and gives all error responses
     * the problem detail body, so the controllers only list their own error codes.
     */
    @Bean
    OpenApiCustomizer problemDetailResponses() {
        // added here and not in the OpenAPI bean: springdoc drops schemas no operation references yet
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemDetailSchema());
            openApi.getPaths().forEach(OpenApiConfig::addProblemResponses);
        };
    }

    private static void addProblemResponses(String path, PathItem item) {
        if (!path.startsWith("/api/")) {
            return;
        }
        item.readOperations().forEach(operation -> {
            ApiResponses responses = responses(operation);
            responses.putIfAbsent("401", new ApiResponse().description("Missing or invalid API key"));
            responses.putIfAbsent("500", new ApiResponse().description("Unexpected server error"));
            responses.forEach((code, response) -> {
                if (code.startsWith("4") || code.startsWith("5")) {
                    response.setContent(new Content().addMediaType(PROBLEM_JSON,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))));
                }
            });
        });
    }

    private static ApiResponses responses(Operation operation) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        return operation.getResponses();
    }

    @SuppressWarnings("rawtypes")
    private static Schema problemDetailSchema() {
        return new ObjectSchema()
                .description("RFC 9457 problem detail. Some errors add fields, such as `errors` for "
                        + "validation failures or `existingDocumentId` for duplicates.")
                .addProperty("type", new StringSchema().example("about:blank"))
                .addProperty("title", new StringSchema().example("Document not found"))
                .addProperty("status", new IntegerSchema().example(404))
                .addProperty("detail", new StringSchema().example("Document 3f2a... not found"))
                .addProperty("instance", new StringSchema().example("/api/documents/3f2a..."));
    }
}
