package com.docmind.document;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSortTest {

    @Test
    void acceptsWhitelistedFields() {
        var pageable = PageRequest.of(0, 10, Sort.by("filename").ascending().and(Sort.by("createdAt").descending()));

        assertThat(DocumentSort.apply(pageable)).isSameAs(pageable);
    }

    @Test
    void rejectsUnknownFields() {
        var pageable = PageRequest.of(0, 10, Sort.by("checksum"));

        assertThatThrownBy(() -> DocumentSort.apply(pageable))
                .isInstanceOf(InvalidDocumentSortException.class)
                .hasMessageContaining("checksum")
                .hasMessageContaining("createdAt");
    }
}
