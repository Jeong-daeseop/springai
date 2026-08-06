package com.krdevops.springai.service.resilience;

import com.krdevops.springai.config.OperationalResilienceProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import com.krdevops.springai.config.observability.ObservabilityContext;
import com.krdevops.springai.config.observability.ObservabilityContextHolder;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 외부 시스템별 circuit breaker와 비대기형 bulkhead를 함께 적용한다. */
@Component
public class ExternalCallGuard {

    public enum CircuitState { CLOSED, OPEN, HALF_OPEN }
    public record Snapshot(CircuitState state, int consecutiveFailures, int availablePermits) {}

    private final int failureThreshold;
    private final long openMillis;
    private final Clock clock;
    private final OperationalTelemetry telemetry;
    private final Map<ExternalDependency, Entry> entries = new EnumMap<>(ExternalDependency.class);

    @Autowired
    public ExternalCallGuard(OperationalResilienceProperties properties, OperationalTelemetry telemetry) {
        this(properties, Clock.systemUTC(), telemetry);
    }

    public ExternalCallGuard(OperationalResilienceProperties properties) {
        this(properties, Clock.systemUTC(), null);
    }

    ExternalCallGuard(OperationalResilienceProperties properties, Clock clock) {
        this(properties, clock, null);
    }

    private ExternalCallGuard(OperationalResilienceProperties properties, Clock clock,
                              OperationalTelemetry telemetry) {
        this.failureThreshold = properties.getCircuitBreaker().getFailureThreshold();
        this.openMillis = properties.getCircuitBreaker().getOpenDuration().toMillis();
        this.clock = clock;
        this.telemetry = telemetry;
        int capture = properties.getBulkhead().getCaptureConcurrency();
        int indexing = properties.getBulkhead().getIndexingConcurrency();
        int chat = properties.getBulkhead().getChatConcurrency();
        entries.put(ExternalDependency.MYSQL, new Entry(indexing));
        entries.put(ExternalDependency.REDIS, new Entry(indexing));
        entries.put(ExternalDependency.OPENAI, new Entry(chat));
        entries.put(ExternalDependency.OLLAMA, new Entry(chat));
        entries.put(ExternalDependency.FIGMA, new Entry(capture));
        entries.put(ExternalDependency.EXTRACTOR, new Entry(capture));
        if (telemetry != null) {
            entries.keySet().forEach(dependency -> telemetry.registerCircuitGauge(dependency,
                    () -> switch (state(entries.get(dependency))) {
                        case CLOSED -> 0;
                        case HALF_OPEN -> 1;
                        case OPEN -> 2;
                    }));
        }
    }

    public <T> T execute(ExternalDependency dependency, Callable<T> action) {
        long startedAt = System.nanoTime();
        Entry entry;
        try {
            entry = acquire(dependency);
        } catch (ExternalCallRejectedException e) {
            record(dependency, e.getMessage().contains("circuit") ? "CIRCUIT_OPEN" : "REJECTED", startedAt);
            throw e;
        }
        try {
            T result = action.call();
            success(entry);
            record(dependency, "SUCCESS", startedAt);
            return result;
        } catch (RuntimeException e) {
            failure(entry);
            record(dependency, isTimeout(e) ? "TIMEOUT" : "FAILURE", startedAt);
            throw e;
        } catch (Exception e) {
            failure(entry);
            record(dependency, isTimeout(e) ? "TIMEOUT" : "FAILURE", startedAt);
            throw new IllegalStateException(dependency + " 외부 호출 실패", e);
        } finally {
            release(entry);
        }
    }

    public <T> Flux<T> protect(ExternalDependency dependency, Supplier<Flux<T>> source) {
        ObservabilityContext capturedContext = ObservabilityContextHolder.current();
        return Flux.defer(() -> {
            long startedAt = System.nanoTime();
            Entry entry;
            try {
                entry = acquire(dependency);
            } catch (ExternalCallRejectedException e) {
                record(capturedContext, dependency,
                        e.getMessage().contains("circuit") ? "CIRCUIT_OPEN" : "REJECTED", startedAt);
                return Flux.error(e);
            }
            AtomicBoolean completed = new AtomicBoolean();
            AtomicBoolean recorded = new AtomicBoolean();
            try {
                return source.get()
                        .doOnComplete(() -> {
                            completed.set(true);
                            success(entry);
                            if (recorded.compareAndSet(false, true)) {
                                record(capturedContext, dependency, "SUCCESS", startedAt);
                            }
                        })
                        .doOnError(error -> {
                            failure(entry);
                            if (recorded.compareAndSet(false, true)) {
                                record(capturedContext, dependency,
                                        isTimeout(error) ? "TIMEOUT" : "FAILURE", startedAt);
                            }
                        })
                        .doFinally(signal -> {
                            if (!completed.get() && signal.name().equals("CANCEL")) {
                                success(entry);
                                if (recorded.compareAndSet(false, true)) {
                                    record(capturedContext, dependency, "SUCCESS", startedAt);
                                }
                            }
                            release(entry);
                        });
            } catch (RuntimeException e) {
                failure(entry);
                record(capturedContext, dependency, isTimeout(e) ? "TIMEOUT" : "FAILURE", startedAt);
                release(entry);
                return Flux.error(e);
            }
        });
    }

    public Map<ExternalDependency, Snapshot> snapshots() {
        Map<ExternalDependency, Snapshot> result = new EnumMap<>(ExternalDependency.class);
        entries.forEach((dependency, entry) -> result.put(dependency,
                new Snapshot(state(entry), entry.failures.get(), entry.bulkhead.availablePermits())));
        return Map.copyOf(result);
    }

    private Entry acquire(ExternalDependency dependency) {
        Entry entry = entries.get(dependency);
        long now = clock.millis();
        long until = entry.openUntil.get();
        if (until > now) throw new ExternalCallRejectedException(dependency, "circuit OPEN");
        if (until > 0 && !entry.halfOpenInFlight.compareAndSet(false, true)) {
            throw new ExternalCallRejectedException(dependency, "HALF_OPEN probe 실행 중");
        }
        if (!entry.bulkhead.tryAcquire()) {
            entry.halfOpenInFlight.set(false);
            throw new ExternalCallRejectedException(dependency, "bulkhead 동시 실행 한도 초과");
        }
        return entry;
    }

    private void success(Entry entry) {
        entry.failures.set(0);
        entry.openUntil.set(0);
        entry.halfOpenInFlight.set(false);
    }

    private void failure(Entry entry) {
        if (entry.failures.incrementAndGet() >= failureThreshold) {
            entry.openUntil.set(clock.millis() + openMillis);
        }
        entry.halfOpenInFlight.set(false);
    }

    private void release(Entry entry) { entry.bulkhead.release(); }

    private CircuitState state(Entry entry) {
        long until = entry.openUntil.get();
        if (until == 0) return CircuitState.CLOSED;
        return until > clock.millis() ? CircuitState.OPEN : CircuitState.HALF_OPEN;
    }

    private void record(ExternalDependency dependency, String outcome, long startedAt) {
        if (telemetry != null) telemetry.externalCall(dependency, outcome, System.nanoTime() - startedAt);
    }

    private void record(ObservabilityContext context, ExternalDependency dependency,
                        String outcome, long startedAt) {
        try (var ignored = ObservabilityContextHolder.open(context)) {
            record(dependency, outcome, startedAt);
        }
    }

    private boolean isTimeout(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getClass().getSimpleName().toLowerCase().contains("timeout")) return true;
            current = current.getCause();
        }
        return false;
    }

    private static final class Entry {
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicLong openUntil = new AtomicLong();
        private final AtomicBoolean halfOpenInFlight = new AtomicBoolean();
        private final Semaphore bulkhead;
        private Entry(int permits) { bulkhead = new Semaphore(permits); }
    }
}
