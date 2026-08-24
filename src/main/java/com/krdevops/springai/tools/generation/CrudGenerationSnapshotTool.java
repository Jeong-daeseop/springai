package com.krdevops.springai.tools.generation;

import com.krdevops.springai.config.mcp.McpToolRisk;
import com.krdevops.springai.config.mcp.McpToolRiskLevel;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.RegionMarkerParser;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlan;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlanner;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 5축 Ownership 체인을 켜기 전 이미 생성돼 있던 화면을 위한 부트스트랩 Tool. 파일을 전혀 쓰지
 * 않고, 지금 디스크에 있는 내용을 그대로 신뢰해 다음 재생성부터 쓸 Base 스냅샷만 등록한다.
 */
@Component
@RequiredArgsConstructor
public class CrudGenerationSnapshotTool {

    private final CrudGenerationPlanner crudGenerationPlanner;
    private final CrudGenerationSnapshotStore snapshotStore;
    private final CodeService codeService;

    @McpToolRisk(McpToolRiskLevel.DB_WRITE)
    @Tool(description = """
            5축 파이프라인 Ownership 보호(app.pipeline-evolution.mode=V2_PREVIEW 이상)를 켜기 전
            이미 생성돼 있던 CRUD 화면을 위한 부트스트랩 Tool입니다.
            지금 디스크에 있는 파일 내용을 그대로 신뢰해 다음 재생성부터 비교 기준(Base)으로 쓸
            스냅샷만 등록하며, 파일은 전혀 건드리지 않습니다.
            이 Tool을 호출하지 않고 기존 화면을 재생성하면, Base가 없어 Current와 New가 조금이라도
            다르면 충돌(BOTH_CHANGED)로 판정되어 사람 검토가 필요합니다.
            database/tableName/domain/packageName/outputPath/viewType은 원래 이 화면을 생성할 때
            썼던 값과 동일해야 합니다.
            이 Tool은 기본 Layout 옵션·eGovFrame 5.0으로 원래 생성됐다고 가정하므로, 비기본
            Layout 옵션으로 생성된 화면은 파일 목록이 어긋나 일부 파일을 못 찾거나 잘못된 위치로
            추정할 수 있습니다.
            """)
    public String adoptCurrentAsBaseline(String database, String tableName, String domain,
            String packageName, String outputPath, String viewType) {
        codeService.validateOutputRoot(outputPath);
        CrudGenerationCommand command = new CrudGenerationCommand(
                database, tableName, domain, packageName, Path.of(outputPath),
                "auto", "5.0", viewType, LayoutOptions.empty(), ProgramMetadataOverrides.empty(),
                DesignContextReference.empty());
        CrudGenerationPlan plan = crudGenerationPlanner.plan(command);
        if (plan.failed()) {
            return "채택 실패 — 화면 계획을 만들 수 없습니다: " + plan.failure().validationSummary();
        }

        List<GenerationOwnershipManifest.ArtifactOwnership> artifacts = new ArrayList<>();
        int adoptedFileCount = 0;
        for (FileBlueprint file : plan.blueprint().files()) {
            String current = readIfExists(file.targetPath());
            if (current == null) {
                continue; // 아직 생성된 적 없는 파일 — 채택할 대상이 없다.
            }
            List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(current);
            List<GenerationOwnershipManifest.Region> manifestRegions = regions.stream()
                    .map(region -> new GenerationOwnershipManifest.Region(
                            region.regionId(), region.regionType(), RegionMarkerParser.hashOf(region.content())))
                    .toList();
            String relative = Path.of(outputPath).relativize(file.targetPath()).toString();
            artifacts.add(new GenerationOwnershipManifest.ArtifactOwnership(
                    relative, manifestRegions, GenerationOwnershipManifest.MergePolicy.REGENERATE, "springai"));
            adoptedFileCount++;
        }

        if (adoptedFileCount == 0) {
            return "채택 실패 — 대상 화면의 파일을 디스크에서 하나도 찾지 못했습니다. "
                    + "database/tableName/domain/packageName/outputPath/viewType이 원래 생성 때와 "
                    + "동일한지 확인하세요.";
        }

        String operationId = CrudGenerationOperationIdFactory.forScreen(outputPath, tableName, viewType);
        GenerationOwnershipManifest manifest = GenerationOwnershipManifest.builder(operationId)
                .artifacts(artifacts).build();
        snapshotStore.save(operationId, manifest);

        return "채택 완료 — " + adoptedFileCount + "개 파일의 현재 내용을 다음 재생성의 Base로 등록했습니다. "
                + "파일은 변경되지 않았습니다.";
    }

    private String readIfExists(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("기존 파일 읽기 실패: " + path, exception);
        }
    }
}
