package com.docmind.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";

    @Bean
    OpenAPI docMindOpenApi(@Value("${docmind.security.header-name:X-API-Key}") String headerName) {
        return new OpenAPI()
                .info(new Info()
                        .title("DocMind AI API")
                        .version("v1")
                        .description("Upload documents, search them semantically and ask questions with cited answers."))
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(headerName)))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
