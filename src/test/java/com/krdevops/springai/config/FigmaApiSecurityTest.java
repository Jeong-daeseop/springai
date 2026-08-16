package com.krdevops.springai.config;

import com.krdevops.springai.config.mcp.McpSecurityProperties;
import com.krdevops.springai.config.mcp.McpCredentialValidator;
import com.krdevops.springai.config.mcp.McpSecurityAuditLogger;
import com.krdevops.springai.service.figma.FigmaRestTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaApiSecurityTest {

    private SecurityConfig config(FigmaRestTokenService tokenService) {
        AppProperties properties = new AppProperties();
        properties.setApiKey("rest-secret");
        McpSecurityProperties mcpProperties = new McpSecurityProperties();
        return new SecurityConfig(properties, tokenService,
                new McpCredentialValidator(mcpProperties), new McpSecurityAuditLogger());
    }

    private FigmaRestTokenService disabledTokenService() {
        return new FigmaRestTokenService("", 900);
    }

    @Test
    void figmaApiRejectsMissingApiKey() throws Exception {
        Filter filter = config(disabledTokenService()).apiKeyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/figma/screens/user-list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid API Key");
    }

    @Test
    void figmaApiAcceptsConfiguredApiKey() throws Exception {
        Filter filter = config(disabledTokenService()).apiKeyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/figma/screens/user-list");
        request.addHeader("X-API-Key", "rest-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void figmaScreensGetAcceptsValidShortLivedBearerToken() throws Exception {
        FigmaRestTokenService tokenService = new FigmaRestTokenService("token-secret", 900);
        String token = tokenService.issue().token();
        Filter filter = config(tokenService).apiKeyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/figma/screens/user-list");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void figmaScreensGetRejectsExpiredOrForgedBearerToken() throws Exception {
        FigmaRestTokenService tokenService = new FigmaRestTokenService("token-secret", 900);
        Filter filter = config(tokenService).apiKeyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/figma/screens/user-list");
        request.addHeader("Authorization", "Bearer forged.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void bearerTokenIsNotAcceptedForNonScreensEndpoints() throws Exception {
        FigmaRestTokenService tokenService = new FigmaRestTokenService("token-secret", 900);
        String token = tokenService.issue().token();
        Filter filter = config(tokenService).apiKeyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/figma/exports");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void pluginScopedTokenAcceptsRefinementCaptureAndReportButRejectsApproval() throws Exception {
        FigmaRestTokenService tokenService = new FigmaRestTokenService("token-secret", 900);
        String token = tokenService.issue(Set.of(
                FigmaRestTokenService.SCOPE_SCREENS_READ,
                FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE,
                FigmaRestTokenService.SCOPE_REPORTS_WRITE)).token();
        Filter filter = config(tokenService).apiKeyFilter();

        assertBearerResult(filter, token, "POST", "/api/figma/refinements/capture", 200);
        assertBearerResult(filter, token, "POST", "/api/figma/operations/reports", 200);
        assertBearerResult(filter, token, "POST", "/api/figma/refinements/p1/approve", 401);
    }

    private void assertBearerResult(Filter filter, String token, String method, String path, int expected)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(expected);
    }
}
