package com.krdevops.springai.service.pipeline;
import com.krdevops.springai.model.artifact.ContentHashes;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Comparator;

@Service
public class ToolDefinitionSnapshotService {
    public Snapshot snapshot(PipelineApiOperationCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("catalog은 필수입니다.");
        return new Snapshot(catalog.operations().stream()
                .sorted(Comparator.comparing(PipelineApiOperationCatalog.Operation::name))
                .toList(), snapshotHash(catalog));
    }

    /** 순서와 포맷이 명시된 canonical representation으로 snapshot drift를 검출한다. */
    public String snapshotHash(PipelineApiOperationCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("catalog은 필수입니다.");
        String canonical = catalog.operations().stream()
                .sorted(Comparator.comparing(PipelineApiOperationCatalog.Operation::name))
                .map(operation -> operation.name() + "|" + operation.risk())
                .collect(Collectors.joining("\n"));
        return ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
    }

    public boolean matches(PipelineApiOperationCatalog catalog, Snapshot expected) {
        if (catalog == null || expected == null) return false;
        return snapshotHash(catalog).equals(expected.hash());
    }

    public record Snapshot(List<PipelineApiOperationCatalog.Operation> operations, String hash) {
        public Snapshot {
            operations = List.copyOf(operations);
            if (hash == null || hash.isBlank()) throw new IllegalArgumentException("snapshot hash는 필수입니다.");
        }
    }
}
