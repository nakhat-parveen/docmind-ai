package com.docmind.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class ApiKey {

    public static final String HEADER = "X-API-Key";
    public static final String TEST_KEY = "test-key";

    private ApiKey() {
    }

    public static RequestPostProcessor apiKey() {
        return request -> {
            request.addHeader(HEADER, TEST_KEY);
            return request;
        };
    }
}
