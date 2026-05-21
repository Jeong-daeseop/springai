package com.krdevops.springai.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Phase 4 — Local LLM (Ollama) 연결 설정.
 *
 * 민감 데이터(주민번호, 의료정보, 인사정보 등)를 외부 API 호출 없이
 * 사내 서버에서만 처리하기 위해 Ollama를 사용한다.
 *
 * Ollama 설치:
 *   brew install ollama
 *   ollama pull mistral       # 분류/요약용 경량 모델
 *   ollama pull codellama     # 코드 생성용 (선택)
 *   ollama serve              # 서버 실행 (localhost:11434)
 */
@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:mistral}")
    private String defaultModel;

    @Bean
    public OllamaChatModel ollamaChatModel() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(10));  // LLM 응답 생성 대기 — 최대 10분

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(new ReactorClientHttpRequestFactory(httpClient));

        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(ollamaBaseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
        OllamaChatOptions options = OllamaChatOptions.builder()
                .model(defaultModel)
                .temperature(0.3)   // 민감 데이터 처리 — 낮은 temperature로 일관성 확보
                .build();
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(options)
                .build();
    }
}
