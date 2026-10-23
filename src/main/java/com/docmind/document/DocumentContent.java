package com.docmind.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * The raw bytes of an uploaded file. Kept in a separate table so that listing
 * documents never loads file contents.
 */
@Entity
@Table(name = "document_contents")
public class DocumentContent {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(nullable = false)
    private byte[] data;

    protected DocumentContent() {
    }

    public DocumentContent(UUID documentId, byte[] data) {
        this.documentId = documentId;
        this.data = data;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public byte[] getData() {
        return data;
    }
}
