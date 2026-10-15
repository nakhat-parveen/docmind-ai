package com.docmind.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic bag-of-words embedding. Texts sharing words end up close to each
 * other, which is enough to test retrieval without calling a real model.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 384;

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(embed(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        for (String word : NON_WORD.split(text.toLowerCase(Locale.ROOT))) {
            if (word.length() > 2) {
                vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
            }
        }

        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f;
            return vector;
        }
        float length = (float) Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= length;
        }
        return vector;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
