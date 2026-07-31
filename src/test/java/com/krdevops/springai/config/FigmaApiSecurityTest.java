package com.krdevops.springai.config;

import com.krdevops.springai.service.figma.FigmaRestTokenService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class FigmaApiSecurityTest {

    private SecurityConfig config(FigmaRestTokenService tokenService) {
        AppProperties properties = new AppProperties();
        properties.setApiKey("rest-secret");
        return new SecurityConfig(properties, tokenService);
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
}
