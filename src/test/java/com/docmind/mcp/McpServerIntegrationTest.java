package com.docmind.mcp;

import com.docmind.ingestion.DocumentType;
import com.docmind.ingestion.IngestionService;
import com.docmind.support.ApiKey;
import com.docmind.support.DatabaseCleaner;
import com.docmind.support.FakeChatModel;
import com.docmind.support.TestAiConfiguration;
import com.docmind.support.TestcontainersConfiguration;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestAiConfiguration.class})
class McpServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private FakeChatModel chatModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private McpSyncClient client;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        chatModel.reset();
        ingestionService.ingest(UUID.randomUUID(), "faq.txt", DocumentType.TEXT,
                "The support hotline is available Monday to Friday from 8 to 18 o'clock.".getBytes());
        client = connect(ApiKey.TEST_KEY);
        client.initialize();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void exposesDocumentTools() {
        var tools = client.listTools().tools();

        assertThat(tools)
                .extracting(McpSchema.Tool::name)
                .containsExactlyInAnyOrder("search_documents", "ask_documents", "list_documents");
    }

    @Test
    void searchToolReturnsMatchingChunks() {
        var result = client.callTool(new McpSchema.CallToolRequest(
                "search_documents", Map.of("query", "When is the support hotline available?", "topK", 3)));

        assertThat(result.isError()).isFalse();
        assertThat(textOf(result)).contains("faq.txt").contains("support hotline");
    }

    @Test
    void askToolAnswersWithCitations() {
        chatModel.reply("The hotline is open Monday to Friday [1].");

        var result = client.callTool(new McpSchema.CallToolRequest(
                "ask_documents", Map.of("question", "When can I call the support hotline?")));

        assertThat(textOf(result))
                .contains("The hotline is open Monday to Friday [1].")
                .contains("faq.txt");
    }

    @Test
    void rejectsClientWithoutValidKey() {
        McpSyncClient unauthorized = connect("wrong-key");

        assertThatThrownBy(unauthorized::initialize).isInstanceOf(RuntimeException.class);
        unauthorized.close();
    }

    private McpSyncClient connect(String apiKey) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .customizeRequest(request -> request.header(ApiKey.HEADER, apiKey))
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build();
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(content -> ((McpSchema.TextContent) content).text())
                .reduce("", String::concat);
    }
}
