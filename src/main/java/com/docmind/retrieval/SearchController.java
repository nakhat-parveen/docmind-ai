package com.docmind.retrieval;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Semantic search over indexed chunks")
public class SearchController {

    private final ChunkSearchService searchService;

    public SearchController(ChunkSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    @Operation(summary = "Return the chunks most similar to the query")
    @ApiResponse(responseCode = "200", description = "Matching chunks, best first; empty when nothing is similar enough")
    @ApiResponse(responseCode = "400", description = "The query is blank, too long, or topK is out of range")
    public List<RetrievedChunk> search(@Valid @RequestBody SearchRequestBody request) {
        return searchService.search(request.query(), request.topK(), request.documentIds());
    }
}
