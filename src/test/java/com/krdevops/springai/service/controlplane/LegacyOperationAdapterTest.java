package com.krdevops.springai.service.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ApprovalMode;
import com.krdevops.springai.model.operation.OperationEvent;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.operation.OperationEventPort;
import com.krdevops.springai.service.thymeleaf.ThymeleafOperationStore;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LegacyOperationAdapterTest {

    @Test
    void Thymeleaf_Adapter는_원본_테이블_PK_상태_Revision을_그대로_노출한다() {
        ThymeleafOperationStore store = mock(ThymeleafOperationStore.class);
        ArtifactService artifacts = mock(ArtifactService.class);
        ValidationGateExecutor gates = mock(ValidationGateExecutor.class);
        String operationId = "thymeleaf-op";
        var operation = new ThymeleafProjectOperation(operationId, "/project",
                ProjectOperationStatus.APPLIED, Map.of("a.html", "html"), List.of("a.html"),
                "/backup", List.of(), List.of(), true, LocalDateTime.now(), LocalDateTime.now());
        var snapshot = new ThymeleafOperationSnapshot(4, operation, "/project", Map.of(),
                "design-revision", "preview-hash");
        given(store.findLatest(operationId)).willReturn(Optional.of(snapshot));
        given(artifacts.findByOperation(operationId)).willReturn(List.of());
        var adapter = new ThymeleafWorkflowAdapter(store, artifacts, gates,
                new OperationStatusNormalizer(), new ObjectMapper().findAndRegisterModules());

        var projected = adapter.find(operationId).orElseThrow();

        assertThat(projected.sourceType()).isEqualTo(GenerationSourceType.THYMELEAF_MIGRATION);
        assertThat(projected.sourceTable()).isEqualTo("AI_THYMELEAF_PROJECT_OPERATION");
        assertThat(projected.sourcePrimaryKey()).isEqualTo(operationId + "/4");
        assertThat(projected.sourceStatus()).isEqualTo("APPLIED");
        assertThat(projected.sourceRevision()).isEqualTo("design-revision");
        assertThat(projected.approvalMode()).isEqualTo(ApprovalMode.EXPLICIT_HASH_APPROVAL);
        assertThat(projected.writePolicy()).isEqualTo(ProjectWritePolicy.ATOMIC_APPROVED);
        assertThat(projected.validationEvidenceStatus()).isEqualTo(EvidenceRecordingStatus.NOT_RECORDED);
        assertThat(projected.callerType()).isEqualTo("UNKNOWN");
        assertThat(projected.environment()).isEqualTo("UNKNOWN");
    }

    @Test
    void Thymeleaf_Adapter는_최신_상태전이의_행위자_호출채널_환경을_투영한다() {
        ThymeleafOperationStore store = mock(ThymeleafOperationStore.class);
        ArtifactService artifacts = mock(ArtifactService.class);
        ValidationGateExecutor gates = mock(ValidationGateExecutor.class);
        OperationEventPort events = mock(OperationEventPort.class);
        String operationId = "thymeleaf-context";
        var operation = new ThymeleafProjectOperation(operationId, "/project",
                ProjectOperationStatus.APPLIED, Map.of("a.html", "html"), List.of("a.html"),
                "/backup", List.of(), List.of(), true, LocalDateTime.now(), LocalDateTime.now());
        var snapshot = new ThymeleafOperationSnapshot(2, operation, "/project", Map.of(), "design", "preview");
        given(store.findLatest(operationId)).willReturn(Optional.of(snapshot));
        given(artifacts.findByOperation(operationId)).willReturn(List.of());
        given(events.findByOperation(operationId)).willReturn(List.of(new OperationEvent(
                "event", operationId, "THYMELEAF_PROJECT", 2, "APPROVED", "APPLIED", "APPLIED",
                "operator-7", "MCP", "stage", "correlation", "hash", Instant.now())));
        var adapter = new ThymeleafWorkflowAdapter(store, artifacts, gates,
                new OperationStatusNormalizer(), new ObjectMapper().findAndRegisterModules(), events);

        var projected = adapter.find(operationId).orElseThrow();

        assertThat(projected.actorId()).isEqualTo("operator-7");
        assertThat(projected.callerType()).isEqualTo("MCP");
        assertThat(projected.environment()).isEqualTo("stage");
    }
}
