package com.docmind.config;

import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void servesSwaggerUi() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
