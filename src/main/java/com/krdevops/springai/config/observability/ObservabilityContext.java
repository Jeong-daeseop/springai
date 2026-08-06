package com.krdevops.springai.config.observability;

/** credential 원문이나 사용자 입력을 포함하지 않는 요청 추적 문맥. */
public record ObservabilityContext(
        String correlationId,
        String operationId,
        String artifactId,
        String actorId,
        String channel) {

    public ObservabilityContext withActor(String actor) {
        return new ObservabilityContext(correlationId, operationId, artifactId, actor, channel);
    }

    public ObservabilityContext withOperation(String operation) {
        return new ObservabilityContext(correlationId, operation, artifactId, actorId, channel);
    }

    public ObservabilityContext withArtifact(String artifact) {
        return new ObservabilityContext(correlationId, operationId, artifact, actorId, channel);
    }
}

