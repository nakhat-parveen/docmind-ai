package com.docmind.config;

import com.docmind.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.docmind.support.ApiKey.apiKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OpenApiIntegrationTest {

    private static final String PROBLEM_REF = "#/components/schemas/ProblemDetail";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publishesApiDescriptionWithoutApiKey() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("DocMind AI API"))
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("X-API-Key"))
                .andExpect(jsonPath("$.paths['/api/documents']").exists())
                .andExpect(jsonPath("$.paths['/api/ask'].post").exists());
    }

    @Test
    void documentsTheStatusCodesOfTheUpload() throws Exception {
        JsonNode responses = apiDocs().at("/paths/~1api~1documents/post/responses");

        assertThat(responses.has("200")).isFalse();
        assertThat(responses.at("/202/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/DocumentResponse");
        for (String code : new String[] {"400", "401", "409", "413", "415", "422", "500"}) {
            assertThat(problemRef(responses, code)).as("response %s", code).isEqualTo(PROBLEM_REF);
        }
    }

    @Test
    void documentsErrorResponsesOfEveryApiOperationAsProblemDetails() throws Exception {
        JsonNode docs = apiDocs();

        assertThat(docs.at("/components/schemas/ProblemDetail/properties").has("detail")).isTrue();
        assertThat(problemRef(docs.at("/paths/~1api~1documents~1{id}/get/responses"), "404")).isEqualTo(PROBLEM_REF);
        assertThat(docs.at("/paths/~1api~1documents~1{id}/delete/responses").has("204")).isTrue();
        assertThat(problemRef(docs.at("/paths/~1api~1ask/post/responses"), "400")).isEqualTo(PROBLEM_REF);

        docs.get("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
            JsonNode responses = operation.getValue().get("responses");
            assertThat(problemRef(responses, "401")).as("401 of %s %s", operation.getKey(), path.getKey())
                    .isEqualTo(PROBLEM_REF);
        }));
    }

    @Test
    void documentedErrorBodyMatchesTheRealResponse() throws Exception {
        mockMvc.perform(get("/api/documents/{id}", UUID.randomUUID()).with(apiKey()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Document not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    void describesPagingAsSeparateQueryParameters() throws Exception {
        JsonNode parameters = apiDocs().at("/paths/~1api~1documents/get/parameters");

        assertThat(parameters.findValuesAsText("name")).contains("page", "size", "sort");
    }

    @Test
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    private JsonNode apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String problemRef(JsonNode responses, String code) {
        return responses.path(code).at("/content/application~1problem+json/schema/$ref").asText();
    }
}
