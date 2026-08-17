package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FigmaContextAnalyzerTest {

    private final FigmaResponseRedactor redactor = new FigmaResponseRedactor();

    @Test
    void analyzeReturnsStructuredEntityFromChatClient() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(FigmaContextAnalyzer.LlmAnalysisResponse.class)).thenReturn(
                new FigmaContextAnalyzer.LlmAnalysisResponse(
                        "user", FigmaScreenType.LIST, LayoutPattern.STANDARD,
                        List.of("krds.table", "krds.button"), 0.1, "명확한 목록 요청"));
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(chatClient, redactor);

        FigmaContextAnalyzer.FigmaContextAnalysis result = analyzer.analyze("사용자 목록 화면", null);

        assertThat(result.domain()).isEqualTo("user");
        assertThat(result.screenType()).isEqualTo(FigmaScreenType.LIST);
        assertThat(result.layoutPattern()).isEqualTo(LayoutPattern.STANDARD);
        assertThat(result.requiredComponentLogicalTypes()).containsExactly("krds.table", "krds.button");
        assertThat(result.hasHighConfidence()).isTrue();
        assertThat(result.uncertain()).isFalse();
    }

    @Test
    void uncertaintyAboveThresholdMarksResultAsUncertain() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(FigmaContextAnalyzer.LlmAnalysisResponse.class)).thenReturn(
                new FigmaContextAnalyzer.LlmAnalysisResponse(
                        "order", FigmaScreenType.DETAIL, LayoutPattern.STANDARD, List.of(), 0.7, "애매함"));
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(chatClient, redactor);

        FigmaContextAnalyzer.FigmaContextAnalysis result = analyzer.analyze("주문 화면", null);

        assertThat(result.uncertain()).isTrue();
        assertThat(result.requiresReview()).isTrue();
    }

    @Test
    void blankPromptIsRejected() {
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(mock(ChatClient.class), redactor);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> analyzer.analyze("", null));
    }

    @Test
    void nullEntityResultFallsBackToUncertain() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(FigmaContextAnalyzer.LlmAnalysisResponse.class)).thenReturn(null);
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(chatClient, redactor);

        FigmaContextAnalyzer.FigmaContextAnalysis result = analyzer.analyze("사용자 목록 화면", null);

        assertThat(result.uncertain()).isTrue();
    }

    /**
     * R6-041: LLM 호출 실패 시 예외 메시지가 그대로 reason에 담기면 Figma access token·LLM key
     * 같은 민감정보가 MCP 응답으로 노출될 수 있다. redactor가 반드시 개입해야 한다.
     */
    @Test
    void llmFailureReasonIsRedactedBeforeReturning() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(
                new RuntimeException("연결 실패: api_key=sk-leakedSecretValue123"));
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(chatClient, redactor);

        FigmaContextAnalyzer.FigmaContextAnalysis result = analyzer.analyze("사용자 목록 화면", null);

        assertThat(result.uncertain()).isTrue();
        assertThat(result.reason()).doesNotContain("sk-leakedSecretValue123");
        assertThat(result.reason()).contains("***REDACTED***");
    }

    /** R6-T09: Spring AI 호출이 timeout으로 실패해도 예외가 전파되지 않고 uncertain fallback으로 처리된다. */
    @Test
    void timeoutFallsBackToUncertainInsteadOfPropagating() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(
                new RuntimeException(new java.util.concurrent.TimeoutException("응답 시간 초과")));
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(chatClient, redactor);

        FigmaContextAnalyzer.FigmaContextAnalysis result = analyzer.analyze("사용자 목록 화면", null);

        assertThat(result.uncertain()).isTrue();
        assertThat(result.requiresReview()).isTrue();
    }

    /** R6-T09: rate limit(429류) 오류도 동일하게 uncertain fallback으로 흡수하고 원인 정보는 redact한다. */
    @Test
    void rateLimitErrorFallsBackToUncertainWithRedactedReason() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(
                new RuntimeException("429 Too Many Requests: api_key=sk-shouldNotLeak"));
        FigmaContextAnalyzer analyzer = new FigmaContextAnalyzer(chatClient, redactor);

        FigmaContextAnalyzer.FigmaContextAnalysis result = analyzer.analyze("사용자 목록 화면", null);

        assertThat(result.uncertain()).isTrue();
        assertThat(result.reason()).doesNotContain("sk-shouldNotLeak");
    }
}
