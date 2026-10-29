package com.docmind.document;

/**
 * The list endpoint was asked to sort by a field that is not in the whitelist.
 */
public class InvalidDocumentSortException extends RuntimeException {

    public InvalidDocumentSortException(String property) {
        super("Cannot sort by '%s'. Allowed fields: %s".formatted(property, DocumentSort.allowedList()));
    }
}
