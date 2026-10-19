package com.docmind.qa;

import com.docmind.retrieval.RetrievedChunk;

import java.util.UUID;

public record Citation(
        int number,
        UUID documentId,
        String filename,
        Integer page,
        int chunkIndex,
        double score,
        String excerpt) {

    private static final int EXCERPT_LENGTH = 300;

    static Citation of(int number, RetrievedChunk chunk) {
        String text = chunk.content().strip().replaceAll("\\s+", " ");
        String excerpt = text.length() > EXCERPT_LENGTH ? text.substring(0, EXCERPT_LENGTH) + "…" : text;
        return new Citation(number, chunk.documentId(), chunk.filename(), chunk.page(),
                chunk.chunkIndex(), chunk.score(), excerpt);
    }
}
