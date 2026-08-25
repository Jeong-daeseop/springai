package com.krdevops.springai.service.generation.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.controlplane.GenerationAuditRecord;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.controlplane.CrudGenerationAuditPort;
import com.krdevops.springai.service.generation.ApprovedWriteConflictGuard;
import com.krdevops.springai.service.generation.GeneratedRegionPreservationService;
import com.krdevops.springai.service.generation.InMemoryCrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.OwnershipConflictDetector;
import com.krdevops.springai.service.generation.SemanticMergePlanService;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeServiceGenerationExecutorControlPlaneTest {

    @TempDir Path outputRoot;

    @Test
    void 성공과_충돌을_Snapshot과_별개의_감사_이력으로_기록한다() throws Exception {
        List<GenerationAuditRecord> audits = new ArrayList<>();
        CrudGenerationAuditPort auditPort = new CrudGenerationAuditPort() {
            @Override public void append(GenerationAuditRecord record) { audits.add(record); }
            @Override public List<GenerationAuditRecord> findAudits(String operationId) { return List.copyOf(audits); }
        };
        var snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        var properties = new PipelineEvolutionProperties();
        properties.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);
        var pathResolver = new SafePathResolver();
        var executor = new CodeServiceGenerationExecutor(new CodeService(properties(outputRoot)),
                new FileSystemApprovedProjectWritePort(pathResolver, new OperationHashFactory(new ObjectMapper())),
                properties, snapshotStore,
                new SemanticMergePlanService(new OwnershipConflictDetector(), new GeneratedRegionPreservationService()),
                new ApprovedWriteConflictGuard(), auditPort);

        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        String initial = region("ORIGINAL");
        executor.execute(plan(target, initial));
        Files.writeString(target, region("HAND_EDITED"));

        var conflict = executor.execute(plan(target, region("GENERATOR_CHANGED")));

        assertThat(conflict.succeededFiles()).isEmpty();
        assertThat(audits).extracting(GenerationAuditRecord::status)
                .containsExactly(GenerationOperationStatus.APPLIED, GenerationOperationStatus.CONFLICT);
        assertThat(audits.get(1).conflictRegionIds()).contains("EmployerServiceImpl.java::custom");
        assertThat(target).hasContent(region("HAND_EDITED"));
    }

    private RenderedGenerationPlan plan(Path target, String content) {
        var file = RenderedFilePlan.rendered(
                new FileBlueprint("serviceImpl", target.getFileName().toString(), target, null), content);
        var context = new GenerationContext("crud", "ebt", "EMP", "emp", "egov.emp",
                outputRoot.toString(), "5.0", "thymeleaf", Map.of());
        return new RenderedGenerationPlan(context, List.of(file), List.of(), List.of());
    }

    private String region(String content) {
        return "// @region:protected:custom start\n" + content
                + "\n// @region:protected:custom end\n";
    }

    private EgovProperties properties(Path path) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(path.toString());
        properties.setOutput(output);
        return properties;
    }
}
