package com.docmind.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Registers the tools directly as MCP tool specifications instead of a {@code ToolCallbackProvider}.
 * A provider bean would also be picked up by the chat model's tool calling setup, which creates a
 * dependency cycle (chat model -> tools -> Q&amp;A service -> chat model) and is not wanted anyway.
 */
@Configuration
public class McpToolsConfig {

    @Bean
    List<SyncToolSpecification> documentToolSpecifications(DocumentTools documentTools) {
        return McpToolUtils.toSyncToolSpecifications(ToolCallbacks.from(documentTools));
    }
}
