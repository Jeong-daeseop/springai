package com.krdevops.springai.config;

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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;

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
            .csrf(csrf -> csrf.ignoringRequestMatchers("/sse", "/mcp/**"))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/sse", "/mcp/**", "/", "/ai/**",
                    "/api/chat/**", "/api/ollama/**", "/api/documents/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
                if (!path.startsWith("/api/")
                        || path.startsWith("/api/chat/")
                        || path.startsWith("/api/ollama/")
                        || path.startsWith("/api/documents/")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String key = request.getHeader("X-API-Key");
                if (appProperties.getApiKey().equals(key)) {
                    // SecurityContext에 인증 정보 등록 → .authenticated() 통과
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken("api-key-user", null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    filterChain.doFilter(request, response);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Invalid API Key\"}");
                }
            }
        };
    }
}
