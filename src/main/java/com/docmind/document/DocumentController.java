package com.docmind.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload and manage source documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF or TXT file. It is indexed in the background; poll the document for its status")
    @ApiResponse(responseCode = "202", description = "Stored with status PENDING; the Location header points to the document")
    @ApiResponse(responseCode = "400", description = "The request has no 'file' part")
    @ApiResponse(responseCode = "409", description = "The same file was already uploaded; see existingDocumentId")
    @ApiResponse(responseCode = "413", description = "The file is larger than 20 MB")
    @ApiResponse(responseCode = "415", description = "The file is not a PDF or TXT file")
    @ApiResponse(responseCode = "422", description = "The file is empty")
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file) throws IOException {
        StoredDocument document = documentService.upload(
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(document.getId())
                .toUri();
        return ResponseEntity.accepted().location(location).body(DocumentResponse.from(document));
    }

    @GetMapping
    @Operation(summary = "List uploaded documents, newest first. Filter by status and filename.")
    @ApiResponse(responseCode = "200", description = "One page of documents")
    @ApiResponse(responseCode = "400", description = "Unknown status or sort field")
    public PageResponse<DocumentResponse> list(
            @Parameter(description = "Only documents with this processing status")
            @RequestParam(required = false) DocumentStatus status,
            @Parameter(description = "Only documents whose filename contains this text (case-insensitive)")
            @RequestParam(required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(documentService.list(new DocumentFilter(status, name), pageable), DocumentResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single document")
    @ApiResponse(responseCode = "200", description = "The document and its processing status")
    @ApiResponse(responseCode = "404", description = "No document with this id")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documentService.get(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and all of its indexed chunks")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No document with this id")
    @ApiResponse(responseCode = "409", description = "The document is being processed right now; try again")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
