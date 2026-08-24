package com.krdevops.springai.service.generation.pipeline.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.ApprovedWriteConflictGuard;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.GeneratedRegionPreservationService;
import com.krdevops.springai.service.generation.InMemoryCrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.OwnershipConflictDetector;
import com.krdevops.springai.service.generation.SemanticMergePlanService;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * usesV2Preview()==true(모드 V2_PREVIEW 이상)일 때의 Ownership-aware Apply 경로를 검증한다.
 * writePort는 Mock이 아니라 실제 {@link FileSystemApprovedProjectWritePort}를 임시 디렉터리에
 * 대고 써서, 스플라이스·ATOMIC_APPROVED drift 감지까지 실제로 동작하는지 확인한다.
 */
class CodeServiceGenerationExecutorOwnershipTest {

    @TempDir
    Path outputRoot;

    private InMemoryCrudGenerationSnapshotStore snapshotStore;

    private CodeServiceGenerationExecutor executor() {
        CodeService codeService = new CodeService(egovProperties(outputRoot));
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);
        snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        return new CodeServiceGenerationExecutor(codeService, writePort, properties, snapshotStore);
    }

    @Test
    void 최초_생성은_충돌없이_저장되고_스냅샷이_생긴다() {
        CodeServiceGenerationExecutor executor = executor();
        RenderedFilePlan file = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null),
                "class EmployerVO {}");
        RenderedGenerationPlan plan = new RenderedGenerationPlan(
                context(outputRoot), List.of(file), List.of(), List.of());

        GenerationExecution execution = executor.execute(plan);

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(execution.succeededFiles()).containsExactly(file);
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent("class EmployerVO {}");
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputRoot.toString(), "EMP", "thymeleaf");
        assertThat(snapshotStore.findLatest(operationId)).isPresent();
    }

    @Test
    void 재생성인데_아무것도_안_바뀌면_충돌없이_저장된다() {
        CodeServiceGenerationExecutor executor = executor();
        String content = "// @region:generated:body start\nA\n// @region:generated:body end\n";
        RenderedFilePlan first = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), content);
        executor.execute(new RenderedGenerationPlan(context(outputRoot), List.of(first), List.of(), List.of()));

        RenderedFilePlan second = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), content);
        GenerationExecution execution = executor.execute(
                new RenderedGenerationPlan(context(outputRoot), List.of(second), List.of(), List.of()));

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent(content);
    }

    @Test
    void 생성기만_바뀐_generated_Region은_자동_반영된다() {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "// @region:generated:body start\nOLD\n// @region:generated:body end\n";
        executor.execute(new RenderedGenerationPlan(context(outputRoot), List.of(RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), v1)),
                List.of(), List.of()));

        String v2 = "// @region:generated:body start\nNEW\n// @region:generated:body end\n";
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("vo", "EmployerVO.java",
                        outputRoot.resolve("EmployerVO.java"), null), v2)), List.of(), List.of()));

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(outputRoot.resolve("EmployerVO.java")).hasContent(v2);
    }

    @Test
    void 사람만_고친_protected_Region은_자동_보존되고_New에_스플라이스된다() throws Exception {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "HEADER\n// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\nFOOTER";
        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v1)), List.of(), List.of()));

        // 사람이 protected 구간만 손으로 고쳤다고 가정 — 디스크 파일을 직접 편집한다.
        Files.writeString(target,
                "HEADER\n// @region:protected:custom start\nHAND_EDITED\n// @region:protected:custom end\nFOOTER");

        // 생성기는 HEADER/FOOTER는 그대로 두고 protected 구간만 다시 ORIGINAL로 만들려 한다(재생성 재현).
        String v2 = "HEADER\n// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\nFOOTER";
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v2)), List.of(), List.of()));

        assertThat(execution.failedFiles()).isEmpty();
        assertThat(target).content().contains("HAND_EDITED").doesNotContain("ORIGINAL");
    }

    @Test
    void 둘_다_바뀐_protected_Region은_Apply_전체를_중단시키고_파일을_안_쓴다() throws Exception {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\n";
        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v1)), List.of(), List.of()));

        Files.writeString(target, "// @region:protected:custom start\nHAND_EDITED\n// @region:protected:custom end\n");
        String v2 = "// @region:protected:custom start\nGENERATOR_CHANGED\n// @region:protected:custom end\n";
        RenderedFilePlan otherFile = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), "class X{}");
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v2), otherFile), List.of(), List.of()));

        assertThat(execution.succeededFiles()).isEmpty(); // 전부 아니면 전무 — otherFile도 안 써짐
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("ownership-guard");
        assertThat(outputRoot.resolve("EmployerVO.java")).doesNotExist();
    }

    @Test
    void ATOMIC_APPROVED_CONFLICT_상태는_write_guard_실패로_변환되고_스냅샷을_갱신하지_않는다() {
        // 진짜 동시성 경합은 결정론적으로 재현하기 어렵다 — writePort를 Mock으로 대체해 CONFLICT를
        // 직접 유도한다. Current를 다시 읽어 drift를 감지하는 것 자체는 이미
        // FileSystemApprovedProjectWritePortTest가 실제 파일로 검증하므로 여기서 중복하지 않는다.
        // 이 테스트는 오직 "execute()가 CONFLICT를 write-guard 실패로 정확히 옮기는지"만 본다.
        ApprovedProjectWritePort writePort = org.mockito.Mockito.mock(ApprovedProjectWritePort.class);
        org.mockito.BDDMockito.given(writePort.apply(org.mockito.ArgumentMatchers.any()))
                .willReturn(ApplyOutcome.conflict(List.of("EmployerVO.java")));
        CodeService codeService = new CodeService(egovProperties(outputRoot));
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);
        InMemoryCrudGenerationSnapshotStore snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        CodeServiceGenerationExecutor executor = new CodeServiceGenerationExecutor(
                codeService, writePort, properties, snapshotStore,
                new SemanticMergePlanService(new OwnershipConflictDetector(), new GeneratedRegionPreservationService()),
                new ApprovedWriteConflictGuard());

        RenderedFilePlan file = RenderedFilePlan.rendered(
                new FileBlueprint("vo", "EmployerVO.java", outputRoot.resolve("EmployerVO.java"), null), "class X{}");
        GenerationExecution execution = executor.execute(
                new RenderedGenerationPlan(context(outputRoot), List.of(file), List.of(), List.of()));

        assertThat(execution.succeededFiles()).isEmpty();
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("write-guard");
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputRoot.toString(), "EMP", "thymeleaf");
        assertThat(snapshotStore.findLatest(operationId)).isEmpty();
    }

    @Test
    void 마커가_깨져_UNKNOWN으로_강등된_Region은_Apply를_차단하고_아무것도_안_쓴다() throws Exception {
        CodeServiceGenerationExecutor executor = executor();
        String v1 = "HEADER\n// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\nFOOTER";
        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v1)), List.of(), List.of()));

        // 사람이 실수로 end 마커를 지웠다고 가정 — RegionMarkerParser가 파일 전체를 unknown.file/UNKNOWN
        // Region 1개로 강등시킨다.
        String broken = "HEADER\n// @region:protected:custom start\nORIGINAL\nFOOTER";
        Files.writeString(target, broken);

        String v2 = "HEADER\n// @region:protected:custom start\nORIGINAL\n// @region:protected:custom end\nFOOTER";
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v2)), List.of(), List.of()));

        assertThat(execution.succeededFiles()).isEmpty();
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("ownership-guard");
        assertThat(target).hasContent(broken);
    }

    @Test
    void 보존_대상_Region이_Current에서_통째로_사라지면_스플라이스_실패로_Apply를_차단한다() throws Exception {
        CodeServiceGenerationExecutor executor = executor();
        // Base/New 모두 protected Region "a"와 "c"를 갖는다 — 각각 Base/New 간 내용이 동일해
        // 3-way 비교 단계에서는 어느 쪽도 개별적으로 문제를 일으키지 않는다.
        String v1 = "// @region:protected:a start\nORIGINAL_A\n// @region:protected:a end\n"
                + "// @region:protected:c start\nORIGINAL_C\n// @region:protected:c end\n";
        Path target = outputRoot.resolve("EmployerServiceImpl.java");
        executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v1)), List.of(), List.of()));

        // 사람이 Region "a"의 마커 쌍(start~end)과 내용을 통째로 삭제했다고 가정 — id를 바꾸거나
        // 마커를 짝 안 맞게 남긴 게 아니라 완전히 지운 것이므로 "c"는 여전히 well-formed하게
        // 남아 RegionMarkerParser가 파일 전체를 UNKNOWN으로 강등시키지 않는다. Current에는 "c" 하나만
        // 파싱된다.
        String handEdited = "// @region:protected:c start\nORIGINAL_C\n// @region:protected:c end\n";
        Files.writeString(target, handEdited);

        // New는 Base와 동일 — "a"는 New에서 여전히 PROTECTED로 타입이 알려져 unknownRegion 강제
        // 승격이 발동하지 않는다. Base/New 모두 "a"를 가지므로 newRegion·baseRegion은 존재하지만
        // Current에는 없어 CURRENT_ONLY로 자연스럽게 분류되고 preservedComparisonIds에 들어간다.
        // spliceRegions()가 Current에서 "a"를 찾지 못해 splicedComparisonIds에 못 넣으므로
        // unresolvedPreserved 체크가 Apply를 차단해야 한다.
        String v2 = v1;
        GenerationExecution execution = executor.execute(new RenderedGenerationPlan(context(outputRoot),
                List.of(RenderedFilePlan.rendered(new FileBlueprint("serviceImpl", "EmployerServiceImpl.java",
                        target, null), v2)), List.of(), List.of()));

        assertThat(execution.succeededFiles()).isEmpty();
        assertThat(execution.failedFiles()).hasSize(1);
        assertThat(execution.failedFiles().get(0).source()).isEqualTo("ownership-guard");
        // Fix 1(Region 소유권 충돌) 경로가 아니라 Fix 2(스플라이스 실패) 경로로 정확히 들어왔는지
        // 메시지 텍스트로 구분한다 — 향후 회귀가 Fix 1 경로로 잘못 흘러도 이 테스트가 잡아낸다.
        assertThat(execution.failedFiles().get(0).description())
                .doesNotContain("Region 소유권 충돌로 Apply 중단")
                .contains("보존 대상 Region을 실제로 복원할 수 없어 Apply 중단")
                .contains("EmployerServiceImpl.java::a");
        assertThat(target).hasContent(handEdited);
    }

    private GenerationContext context(Path outputPath) {
        return new GenerationContext(
                "crud", "ebt", "EMP", "emp", "egovframework.let.emp",
                outputPath.toString(), "5.0", "thymeleaf", Map.of());
    }

    private EgovProperties egovProperties(Path basePath) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(basePath.toString());
        properties.setOutput(output);
        return properties;
    }
}
