package com.krdevops.springai.config;

import com.krdevops.springai.config.mcp.McpAuthenticationInterceptor;
import com.krdevops.springai.config.mcp.McpCredentialValidator;
import com.krdevops.springai.config.mcp.McpSecurityAuditLogger;
import com.krdevops.springai.service.figma.FigmaRestTokenService;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;
    private final FigmaRestTokenService figmaRestTokenService;
    private final McpCredentialValidator mcpCredentialValidator;
    private final McpSecurityAuditLogger mcpSecurityAuditLogger;

    /** DEC-10=REST 경로에서 Plugin이 fetch()할 때 브라우저가 보내는 CORS preflight를 허용할 origin(콤마 구분). */
    @org.springframework.beans.factory.annotation.Value("${app.figma.rest-allowed-origins:}")
    private String figmaRestAllowedOriginsRaw = "";

    /**
     * /api/** 경로는 X-API-Key 헤더 검증.
     * /sse, /mcp/** (Claude Desktop SSE 연결)는 인증 없이 허용.
     */
    /** 기본 사용자 자동 생성 비활성화 — API Key 인증만 사용 */
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF: STATELESS 세션이므로 현재 쿠키 인증 없어 실질 위험 없음.
            // 브라우저 쿠키 인증 도입 시 ignoringRequestMatchers 범위 재검토 필요.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/mcp/**", "/sse/**", "/api/**"))
            .cors(cors -> cors.configurationSource(figmaRestCorsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp/**", "/sse/**", "/", "/ai/**",
                    "/api/chat/**", "/api/ollama/**", "/api/documents/**",
                    "/js/**", "/css/**", "/images/**", "/webjars/**",
                    "/actuator/health", "/actuator/health/**",
                    "/actuator/metrics", "/actuator/metrics/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            // ARCH-0106: /mcp/**, /sse/**는 여전히 permitAll이지만, 이 필터가 요청마다
            // McpActorContext를 채워 ToolAuthorizationPolicy가 Tool 호출 시점에 판단할 수 있게 한다.
            .addFilterBefore(mcpAuthenticationInterceptor(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public McpAuthenticationInterceptor mcpAuthenticationInterceptor() {
        return new McpAuthenticationInterceptor(mcpCredentialValidator, mcpSecurityAuditLogger);
    }

    /**
     * R6-012: `app.figma.rest-allowed-origins`가 비어 있으면(기본값) CORS를 전혀 허용하지 않는다.
     * DEC-10=REST를 채택해 Figma Plugin UI(iframe)가 직접 fetch()하도록 할 때만 값을 채운다.
     */
    @Bean
    public CorsConfigurationSource figmaRestCorsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> allowedOrigins = List.of(figmaRestAllowedOriginsRaw.split(",")).stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (!allowedOrigins.isEmpty()) {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowedMethods(List.of("GET", "POST"));
            configuration.setAllowedHeaders(List.of(
                    "Authorization", "X-API-Key", "If-None-Match", "Content-Type"));
            configuration.setExposedHeaders(List.of("ETag"));
            configuration.setMaxAge(600L);
            source.registerCorsConfiguration("/api/figma/screens/**", configuration);
            source.registerCorsConfiguration("/api/figma/operations/reports", configuration);
            source.registerCorsConfiguration("/api/figma/refinements/**", configuration);
        }
        return source;
    }

    @Bean
    public OncePerRequestFilter apiKeyFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                String path = request.getRequestURI();
                // CORS preflight에는 API Key가 없으므로 인증 필터보다 CorsFilter가 응답하게 한다.
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!path.startsWith("/api/")
                        || path.startsWith("/api/chat/")
                        || path.startsWith("/api/ollama/")
                        || path.startsWith("/api/documents/")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String key = request.getHeader("X-API-Key");
                if (key != null && key.equals(appProperties.getApiKey())) {
                    authenticate("api-key-user");
                    filterChain.doFilter(request, response);
                    return;
                }

                // R6-012/MR-A06: DEC-10=REST에서 Plugin이 장기 X-API-Key 대신 쓰는 단기 토큰.
                // 경로·메서드별로 필요한 Scope가 있는 경우에만 허용해, 승인(approve/reject)처럼
                // 운영자 전용 동작은 이 Token으로 절대 통과하지 못하게 한다(MR-DEC-05).
                String requiredScope = requiredScopeFor(request.getMethod(), path);
                if (requiredScope != null) {
                    String bearer = request.getHeader("Authorization");
                    String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : null;
                    if (token != null && figmaRestTokenService.verifyWithScopes(token).hasScope(requiredScope)) {
                        authenticate("figma-rest-token-user");
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid API Key\"}");
            }
        };
    }

    /**
     * MR-A06: 경로·메서드별로 Plugin 단기 Token이 가져야 할 Scope. null이면 이 Token으로는
     * 절대 접근할 수 없고 {@code X-API-Key}(운영자 인증)만 허용한다 — 승인·반려가 여기 해당한다.
     */
    String requiredScopeFor(String method, String path) {
        boolean isGet = "GET".equalsIgnoreCase(method);
        boolean isPost = "POST".equalsIgnoreCase(method);
        if (isGet && path.startsWith("/api/figma/screens/")) {
            return FigmaRestTokenService.SCOPE_SCREENS_READ;
        }
        if (isGet && (path.startsWith("/api/figma/refinements/screens/")
                || path.matches("^/api/figma/refinements/[^/]+$"))) {
            return FigmaRestTokenService.SCOPE_SCREENS_READ;
        }
        if (isPost && ("/api/figma/refinements/preview".equals(path)
                || "/api/figma/refinements/capture".equals(path))) {
            return FigmaRestTokenService.SCOPE_REFINEMENTS_WRITE;
        }
        if (isPost && "/api/figma/operations/reports".equals(path)) {
            return FigmaRestTokenService.SCOPE_REPORTS_WRITE;
        }
        return null;
    }

    private void authenticate(String principal) {
        // SecurityContext에 인증 정보 등록 → .authenticated() 통과
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        ObservabilityContextHolder.setActor(principal);
    }
}
