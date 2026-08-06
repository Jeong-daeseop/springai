package com.krdevops.springai.config;

import com.krdevops.springai.service.resilience.ExternalCallGuard;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 외부 시스템을 직접 호출하지 않고 circuit 상태만 readiness에 반영한다. */
@Component("externalDependencies")
public class ExternalDependenciesHealthIndicator implements HealthIndicator {

    private final ExternalCallGuard guard;

    public ExternalDependenciesHealthIndicator(ExternalCallGuard guard) {
        this.guard = guard;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        boolean open = guard.snapshots().entrySet().stream().anyMatch(entry -> {
            details.put(entry.getKey().name().toLowerCase(), entry.getValue().state().name());
            return entry.getValue().state() == ExternalCallGuard.CircuitState.OPEN;
        });
        return (open ? Health.down() : Health.up()).withDetails(details).build();
    }
}

