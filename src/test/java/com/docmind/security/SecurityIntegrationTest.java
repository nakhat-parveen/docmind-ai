package com.docmind.security;

import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.docmind.support.ApiKey.HEADER;
import static com.docmind.support.ApiKey.apiKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestWithoutApiKey() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void rejectsWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/documents").header(HEADER, "not-the-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidApiKey() throws Exception {
        mockMvc.perform(get("/api/documents").with(apiKey()))
                .andExpect(status().isOk());
    }

    @Test
    void keepsHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void keepsPrometheusEndpointPublic() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void protectsOtherActuatorEndpoints() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsMcpEndpoint() throws Exception {
        mockMvc.perform(get("/mcp"))
                .andExpect(status().isUnauthorized());
    }
}
