package com.docmind.mcp;

import com.docmind.document.DocumentResponse;
import com.docmind.document.DocumentService;
import com.docmind.qa.Answer;
import com.docmind.qa.QuestionAnswerService;
import com.docmind.retrieval.ChunkSearchService;
import com.docmind.retrieval.RetrievedChunk;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tools published over MCP so that agents and IDEs can query the document base.
 */
@Component
public class DocumentTools {

    private static final int MAX_TOP_K = 20;
    private static final int MAX_LISTED_DOCUMENTS = 100;

    private final ChunkSearchService searchService;
    private final QuestionAnswerService questionAnswerService;
    private final DocumentService documentService;

    public DocumentTools(ChunkSearchService searchService,
                         QuestionAnswerService questionAnswerService,
                         DocumentService documentService) {
        this.searchService = searchService;
        this.questionAnswerService = questionAnswerService;
        this.documentService = documentService;
    }

    @Tool(name = "search_documents",
            description = "Semantic search over all uploaded documents. Returns the most relevant text chunks "
                    + "with filename, page and similarity score.")
    public List<RetrievedChunk> searchDocuments(
            @ToolParam(description = "What to search for, in natural language") String query,
            @ToolParam(description = "Maximum number of chunks to return (1-20)", required = false) Integer topK) {
        return searchService.search(query, clamp(topK), List.of());
    }

    @Tool(name = "ask_documents",
            description = "Answers a question using only the uploaded documents. "
                    + "The answer contains [n] markers that refer to the returned citations.")
    public Answer askDocuments(@ToolParam(description = "The question to answer") String question) {
        return questionAnswerService.ask(question, null, List.of());
    }

    @Tool(name = "list_documents", description = "Lists the uploaded documents, newest first.")
    public List<DocumentResponse> listDocuments() {
        var page = PageRequest.of(0, MAX_LISTED_DOCUMENTS, Sort.by(Sort.Direction.DESC, "createdAt"));
        return documentService.list(page).map(DocumentResponse::from).getContent();
    }

    private static Integer clamp(Integer topK) {
        return topK == null ? null : Math.max(1, Math.min(topK, MAX_TOP_K));
    }
}
