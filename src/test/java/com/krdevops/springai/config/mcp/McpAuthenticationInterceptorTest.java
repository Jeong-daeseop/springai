package com.krdevops.springai.config.mcp;

import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import com.krdevops.springai.config.observability.ObservabilityFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** ARCH-0116: 인증 누락·오류·정상 token 시나리오. */
class McpAuthenticationInterceptorTest {

    @AfterEach
    void clearActor() {
        McpActorContext.clear();
        ObservabilityContextHolder.clear();
    }

    private McpAuthenticationInterceptor interceptor(String sharedToken) {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setSharedToken(sharedToken);
        return new McpAuthenticationInterceptor(
                new McpCredentialValidator(properties), new McpSecurityAuditLogger());
    }

    @Test
    void missingToken_resultsInAnonymousActor() throws Exception {
        McpAuthenticationInterceptor filter = interceptor("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/messages");
        McpActorContext[] observed = new McpActorContext[1];

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed[0] = McpActorContext.current());

        assertThat(observed[0].authenticated()).isFalse();
        assertThat(observed[0].failureCode()).isEqualTo(McpAuthenticationFailureCode.MCP_TOKEN_MISSING);
        // Filter 종료 후에는 반드시 정리되어야 한다(스레드풀 재사용 시 컨텍스트 누수 방지).
        assertThat(McpActorContext.current()).isEqualTo(McpActorContext.ANONYMOUS);
    }

    @Test
    void wrongToken_resultsInAnonymousActor() throws Exception {
        McpAuthenticationInterceptor filter = interceptor("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/messages");
        request.addHeader("X-MCP-Token", "wrong-token");
        McpActorContext[] observed = new McpActorContext[1];

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed[0] = McpActorContext.current());

        assertThat(observed[0].authenticated()).isFalse();
        assertThat(observed[0].failureCode()).isEqualTo(McpAuthenticationFailureCode.MCP_TOKEN_INVALID);
    }

    @Test
    void correctToken_resultsInAuthenticatedActor() throws Exception {
        McpAuthenticationInterceptor filter = interceptor("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/messages");
        request.addHeader("X-MCP-Token", "expected-token");
        McpActorContext[] observed = new McpActorContext[1];

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed[0] = McpActorContext.current());

        assertThat(observed[0].authenticated()).isTrue();
    }

    @Test
    void noSharedTokenConfigured_alwaysAnonymous_evenWithHeaderPresent() throws Exception {
        McpAuthenticationInterceptor filter = interceptor("");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/messages");
        request.addHeader("X-MCP-Token", "anything");
        McpActorContext[] observed = new McpActorContext[1];

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> observed[0] = McpActorContext.current());

        assertThat(observed[0].authenticated()).isFalse();
    }

    @Test
    void unrelatedPath_isNotIntercepted() throws Exception {
        McpAuthenticationInterceptor filter = interceptor("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void exactStreamableHttpEndpoint_isIntercepted() throws Exception {
        McpAuthenticationInterceptor filter = interceptor("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("X-MCP-Token", "expected-token");
        McpActorContext[] observed = new McpActorContext[1];

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> observed[0] = McpActorContext.current());

        assertThat(observed[0].authenticated()).isTrue();
    }

    @Test
    void observabilityFilter가_생성한_correlationId를_MCP인증까지_그대로_전파한다() throws Exception {
        ObservabilityFilter observabilityFilter = new ObservabilityFilter();
        McpAuthenticationInterceptor filter = interceptor("expected-token");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("X-MCP-Token", "expected-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observed = new String[2];

        observabilityFilter.doFilter(request, response, (req, res) ->
                filter.doFilter(req, res, (nestedRequest, nestedResponse) -> {
                    observed[0] = ObservabilityContextHolder.current().correlationId();
                    observed[1] = McpActorContext.current().correlationId();
                }));

        assertThat(observed[0]).isEqualTo(response.getHeader(ObservabilityFilter.CORRELATION_HEADER));
        assertThat(observed[1]).isEqualTo(observed[0]);
    }
}
