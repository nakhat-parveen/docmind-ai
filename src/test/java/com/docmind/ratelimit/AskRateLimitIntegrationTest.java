package com.docmind.ratelimit;

import com.docmind.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.docmind.support.ApiKey.HEADER;
import static com.docmind.support.ApiKey.TEST_KEY;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Own application context so the tight limit does not affect the other ask tests.
 */
@IntegrationTest
@DirtiesContext
@TestPropertySource(properties = {
        "docmind.rate-limit.enabled=true",
        "docmind.rate-limit.capacity=2",
        "docmind.rate-limit.refill-tokens=2",
        "docmind.rate-limit.refill-period=1h",
        "docmind.security.api-keys=test-key,other-key"
})
class AskRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTooManyRequestsAfterTheBurstAndKeepsOtherKeysWorking() throws Exception {
        ask(TEST_KEY).andExpect(status().isOk());
        ask(TEST_KEY).andExpect(status().isOk());

        ask(TEST_KEY)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, greaterThan("0")))
                .andExpect(jsonPath("$.title").value("Too many requests"))
                .andExpect(jsonPath("$.status").value(429));

        ask("other-key").andExpect(status().isOk());
    }

    private ResultActions ask(String apiKey) throws Exception {
        return mockMvc.perform(post("/api/ask")
                .header(HEADER, apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"question": "Is anyone there?"}
                        """));
    }
}
