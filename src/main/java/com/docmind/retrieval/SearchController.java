package com.docmind.retrieval;

import io.swagger.v3.oas.annotations.Operation;
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
    public List<RetrievedChunk> search(@Valid @RequestBody SearchRequestBody request) {
        return searchService.search(request.query(), request.topK(), request.documentIds());
    }
}
