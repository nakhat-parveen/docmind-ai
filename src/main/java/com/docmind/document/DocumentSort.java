package com.docmind.document;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Only these fields can be used as {@code ?sort=}. Anything else used to become a 500
 * from Spring Data; now it is rejected before the query runs.
 */
final class DocumentSort {

    static final Set<String> ALLOWED = Set.of("filename", "createdAt", "sizeBytes", "status");

    private DocumentSort() {
    }

    static Pageable apply(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED.contains(order.getProperty())) {
                throw new InvalidDocumentSortException(order.getProperty());
            }
        }
        return pageable;
    }

    static String allowedList() {
        return ALLOWED.stream().sorted().collect(Collectors.joining(", "));
    }
}
