package com.krdevops.springai.config.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StructuredJsonLogLayoutTest {

    @Test
    void JSON_schema와_MDC_context를_출력하고_secret은_제거한다() throws Exception {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getTimeStamp()).thenReturn(1_700_000_000_000L);
        when(event.getLevel()).thenReturn(Level.INFO);
        when(event.getLoggerName()).thenReturn("test.logger");
        when(event.getThreadName()).thenReturn("test-thread");
        when(event.getFormattedMessage()).thenReturn(
                "password=hunter2 Authorization=Bearer abc.secret.token file-content=TOP_SECRET_FILE "
                        + "debug=" + "x".repeat(5_000));
        when(event.getMDCPropertyMap()).thenReturn(Map.of(
                "eventName", "operation_transition",
                "correlationId", "corr-1",
                "operationId", "op-1",
                "artifactId", "art-1",
                "actorId", "actor-1",
                "channel", "REST"));

        String json = new StructuredJsonLogLayout().doLayout(event);
        var parsed = new ObjectMapper().readTree(json);

        assertThat(parsed.path("event").asText()).isEqualTo("operation_transition");
        assertThat(parsed.path("correlationId").asText()).isEqualTo("corr-1");
        assertThat(parsed.path("operationId").asText()).isEqualTo("op-1");
        assertThat(parsed.path("artifactId").asText()).isEqualTo("art-1");
        assertThat(json).doesNotContain("hunter2", "abc.secret.token", "TOP_SECRET_FILE", "x".repeat(4_500));
        assertThat(json).contains("[REDACTED]", "[TRUNCATED]");
    }
}
