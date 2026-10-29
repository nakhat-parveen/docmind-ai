package com.docmind.document;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFilterTest {

    @Test
    void treatsPercentAndUnderscoreAsLiteralCharacters() {
        assertThat(DocumentFilter.escapeLike("100%_done")).isEqualTo("100\\%\\_done");
        assertThat(DocumentFilter.escapeLike("a\\b")).isEqualTo("a\\\\b");
    }
}
