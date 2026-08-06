package com.krdevops.springai.service.resilience;

import com.krdevops.springai.config.OperationalResilienceProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalCallGuardTest {

    @Test
    void 외부_장애는_해당_adapter_circuit만_열고_다른_기능은_계속_실행한다() {
        OperationalResilienceProperties properties = new OperationalResilienceProperties();
        properties.getCircuitBreaker().setFailureThreshold(2);
        ExternalCallGuard guard = new ExternalCallGuard(properties);
        AtomicInteger attempts = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> guard.execute(ExternalDependency.FIGMA, () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("injected failure");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertThatThrownBy(() -> guard.execute(ExternalDependency.FIGMA, () -> "never"))
                .isInstanceOf(ExternalCallRejectedException.class)
                .hasMessageContaining("circuit OPEN");
        assertThat(guard.execute(ExternalDependency.MYSQL, () -> "unrelated endpoint"))
                .isEqualTo("unrelated endpoint");
        assertThat(attempts).hasValue(2);
        assertThat(guard.snapshots().get(ExternalDependency.FIGMA).state())
                .isEqualTo(ExternalCallGuard.CircuitState.OPEN);
        assertThat(guard.snapshots().get(ExternalDependency.MYSQL).state())
                .isEqualTo(ExternalCallGuard.CircuitState.CLOSED);
    }

    @Test
    void bulkhead는_대기열_없이_동시실행_상한을_초과한_호출을_거부한다() throws Exception {
        OperationalResilienceProperties properties = new OperationalResilienceProperties();
        properties.getBulkhead().setCaptureConcurrency(1);
        ExternalCallGuard guard = new ExternalCallGuard(properties);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        Thread holder = new Thread(() -> guard.execute(ExternalDependency.EXTRACTOR, () -> {
            entered.countDown();
            release.await();
            return null;
        }));
        holder.start();
        entered.await();

        assertThatThrownBy(() -> guard.execute(ExternalDependency.EXTRACTOR, () -> null))
                .isInstanceOf(ExternalCallRejectedException.class)
                .hasMessageContaining("bulkhead");
        release.countDown();
        holder.join();
    }

    @Test
    void 외부_latency_failure_circuit_state를_metric으로_기록한다() {
        OperationalResilienceProperties properties = new OperationalResilienceProperties();
        properties.getCircuitBreaker().setFailureThreshold(1);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExternalCallGuard guard = new ExternalCallGuard(properties, new OperationalTelemetry(registry));

        assertThatThrownBy(() -> guard.execute(ExternalDependency.REDIS, () -> {
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> guard.execute(ExternalDependency.REDIS, () -> "blocked"))
                .isInstanceOf(ExternalCallRejectedException.class);

        assertThat(registry.get("springai.external.calls.total")
                .tag("dependency", "REDIS").tag("outcome", "FAILURE").counter().count()).isEqualTo(1);
        assertThat(registry.get("springai.external.calls.total")
                .tag("dependency", "REDIS").tag("outcome", "CIRCUIT_OPEN").counter().count()).isEqualTo(1);
        assertThat(registry.get("springai.external.circuit.state")
                .tag("dependency", "REDIS").gauge().value()).isEqualTo(2);
    }
}
