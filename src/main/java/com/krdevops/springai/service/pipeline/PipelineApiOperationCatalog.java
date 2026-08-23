package com.krdevops.springai.service.pipeline;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파이프라인 외부에 노출할 작업의 단일 목록이다.
 * 실제 실행 권한은 각 facade의 guard가 결정하며, 이 카탈로그는 discoverability와
 * snapshot/handoff 계약에만 사용한다.
 */
@Service
public class PipelineApiOperationCatalog {
    private static final Set<String> SUPPORTED_RISKS = Set.of("READ", "PREVIEW", "REVIEW", "APPROVE", "RETRY");
    private static final List<Operation> OPERATIONS = List.of(
            new Operation("previewComponentMapping", "PREVIEW"),
            new Operation("publishDesignSystemSnapshot", "APPROVE"),
            new Operation("previewGenerationScope", "PREVIEW"),
            new Operation("getPreviewEvidence", "READ"),
            new Operation("reviewSessionDecision", "REVIEW"),
            new Operation("getScreenHandoff", "READ"),
            new Operation("retryGenerationJob", "RETRY"));

    private final Map<String, Operation> byName = OPERATIONS.stream()
            .collect(Collectors.toUnmodifiableMap(Operation::name, Function.identity()));

    public List<Operation> operations() {
        return OPERATIONS;
    }

    public Operation require(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("operation name은 필수입니다.");
        }
        Operation operation = byName.get(name);
        if (operation == null) {
            throw new IllegalArgumentException("지원하지 않는 pipeline operation입니다: " + name);
        }
        return operation;
    }

    public boolean contains(String name) {
        return name != null && byName.containsKey(name);
    }

    public List<Operation> operationsByRisk(String risk) {
        if (risk == null || risk.isBlank()) return List.of();
        return OPERATIONS.stream().filter(operation -> operation.risk().equals(risk)).toList();
    }

    public Set<String> supportedRisks() {
        return SUPPORTED_RISKS;
    }

    public List<String> operationNames() {
        return OPERATIONS.stream().map(Operation::name).sorted().toList();
    }

    public Map<String, List<Operation>> groupedByRisk() {
        return OPERATIONS.stream().collect(Collectors.collectingAndThen(
                Collectors.groupingBy(Operation::risk, java.util.LinkedHashMap::new, Collectors.toList()),
                groups -> groups.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())))));
    }

    public record Operation(String name, String risk) {
        public Operation {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("operation name은 필수입니다.");
            if (risk == null || risk.isBlank()) throw new IllegalArgumentException("operation risk는 필수입니다.");
            if (!SUPPORTED_RISKS.contains(risk)) throw new IllegalArgumentException("지원하지 않는 operation risk입니다: " + risk);
        }
    }
}
