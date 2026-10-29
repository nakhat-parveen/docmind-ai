package com.docmind.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class DatabaseCleaner {

    private DatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events, vector_store, document_contents, documents");
    }
}
