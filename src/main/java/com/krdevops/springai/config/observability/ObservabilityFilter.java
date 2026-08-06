package com.krdevops.springai.config.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ObservabilityFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ATTRIBUTE = ObservabilityFilter.class.getName() + ".correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = correlationId(request.getHeader(CORRELATION_HEADER));
        request.setAttribute(CORRELATION_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        ObservabilityContext context = new ObservabilityContext(
                correlationId, null, null, "anonymous", channel(request.getRequestURI()));
        try (var ignored = ObservabilityContextHolder.open(context)) {
            filterChain.doFilter(request, response);
        } finally {
            ObservabilityContextHolder.clear();
        }
    }

    private String correlationId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,128}")) return supplied;
        return UUID.randomUUID().toString();
    }

    private String channel(String path) {
        if (path.equals("/mcp") || path.startsWith("/mcp/")) return "MCP";
        if (path.equals("/sse") || path.startsWith("/sse/") || path.startsWith("/api/chat/")) return "SSE";
        if (path.startsWith("/api/")) return "REST";
        return "WEB";
    }
}
