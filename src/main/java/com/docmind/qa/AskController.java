package com.docmind.qa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ask")
@Tag(name = "Q&A", description = "Ask questions answered from the uploaded documents")
public class AskController {

    private final QuestionAnswerService questionAnswerService;

    public AskController(QuestionAnswerService questionAnswerService) {
        this.questionAnswerService = questionAnswerService;
    }

    @PostMapping
    @Operation(summary = "Answer a question with citations to the source chunks")
    @ApiResponse(responseCode = "200", description = "The answer with its citations; says so when no source was found")
    @ApiResponse(responseCode = "400", description = "The question is blank, too long, or topK is out of range")
    @ApiResponse(responseCode = "429", description = "This API key has asked too many questions; see Retry-After")
    public Answer ask(@Valid @RequestBody AskRequest request) {
        return questionAnswerService.ask(request.question(), request.topK(), request.documentIds());
    }
}
