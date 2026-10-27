package com.docmind.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keepsTheIdSentByTheClient() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "order-42");
        var response = new MockHttpServletResponse();
        var seenInMdc = new AtomicReference<String>();

        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInMdc.set(MDC.get(CorrelationId.MDC_KEY));
            }
        });

        assertThat(seenInMdc.get()).isEqualTo("order-42");
        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("order-42");
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void createsAnIdWhenTheClientSentNone() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER)).hasSize(36);
    }

    @Test
    void replacesIdsThatAreNotSafeToLog() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "bad\nvalue");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER)).isNotEqualTo("bad\nvalue").hasSize(36);
    }
}
