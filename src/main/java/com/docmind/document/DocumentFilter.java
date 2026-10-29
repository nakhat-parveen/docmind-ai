package com.docmind.document;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Optional filters for the document list. A {@code null} field means "no filter".
 *
 * @param status only documents with this status
 * @param name   only documents whose filename contains this text, ignoring case
 */
public record DocumentFilter(@Nullable DocumentStatus status, @Nullable String name) {

    private static final char ESCAPE = '\\';

    public static DocumentFilter none() {
        return new DocumentFilter(null, null);
    }

    Specification<StoredDocument> toSpecification() {
        Specification<StoredDocument> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (StringUtils.hasText(name)) {
            String pattern = "%" + escapeLike(name.trim().toLowerCase(Locale.ROOT)) + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("filename")), pattern, ESCAPE));
        }
        return spec;
    }

    /**
     * Makes {@code %} and {@code _} in the search text match themselves instead of
     * acting as wildcards.
     */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
