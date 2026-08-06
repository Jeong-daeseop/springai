package com.krdevops.springai.service.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.krdevops.springai.config.observability.ObservabilityContext;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalTelemetryCorrelationTest {

    @Test
    void operation과_artifact_event가_같은_correlation_actor를_유지한다() {
        Logger logger = (Logger) LoggerFactory.getLogger("springai.telemetry");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (var request = ObservabilityContextHolder.open(
                new ObservabilityContext("corr-e2e-1", null, null, "api-key-user", "REST"))) {
            OperationalTelemetry telemetry = new OperationalTelemetry(new SimpleMeterRegistry());
            telemetry.operationTransition("op-1", "THYMELEAF_PROJECT", "APPROVED", "APPLIED", "APPLIED");
            try (var operation = ObservabilityContextHolder.openOperation("op-1")) {
                telemetry.artifactAction("art-1", "THYMELEAF_PREVIEW", "LINK", "SUCCESS", 10);
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            ObservabilityContextHolder.clear();
        }

        assertThat(appender.list).hasSize(2).allSatisfy(event -> {
            assertThat(event.getMDCPropertyMap().get("correlationId")).isEqualTo("corr-e2e-1");
            assertThat(event.getMDCPropertyMap().get("actorId")).isEqualTo("api-key-user");
            assertThat(event.getMDCPropertyMap().get("operationId")).isEqualTo("op-1");
        });
        assertThat(appender.list.get(1).getMDCPropertyMap().get("artifactId")).isEqualTo("art-1");
    }
}

