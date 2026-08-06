package com.krdevops.springai.config.observability;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityFilterTest {

    @Test
    void REST_요청의_correlation과_channel을_설정하고_응답에_반환한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/operations/status");
        request.addHeader(ObservabilityFilter.CORRELATION_HEADER, "corr-contract-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ObservabilityContext> observed = new AtomicReference<>();

        new ObservabilityFilter().doFilter(request, response,
                (servletRequest, servletResponse) -> observed.set(ObservabilityContextHolder.current()));

        assertThat(observed.get().correlationId()).isEqualTo("corr-contract-1");
        assertThat(observed.get().channel()).isEqualTo("REST");
        assertThat(response.getHeader(ObservabilityFilter.CORRELATION_HEADER)).isEqualTo("corr-contract-1");
        assertThat(org.slf4j.MDC.get("correlationId")).isNull();
    }

    @Test
    void 유효하지_않은_correlation은_새_UUID로_교체한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp");
        request.addHeader(ObservabilityFilter.CORRELATION_HEADER, "bad value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ObservabilityContext> observed = new AtomicReference<>();

        new ObservabilityFilter().doFilter(request, response,
                (servletRequest, servletResponse) -> observed.set(ObservabilityContextHolder.current()));

        assertThat(observed.get().correlationId()).matches("[0-9a-f-]{36}");
        assertThat(observed.get().channel()).isEqualTo("MCP");
    }
}

