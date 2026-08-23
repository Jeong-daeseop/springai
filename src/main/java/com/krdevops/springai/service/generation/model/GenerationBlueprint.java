package com.krdevops.springai.service.generation.model;

import java.util.List;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Planner 산출물 — 무엇을 어디에 만들고 어떤 Processor를 어떤 순서로 실행할지에 대한 계획.
 * 명세서 §10.3. Source 문자열은 보유하지 않는다(렌더링은 {@code GenerationRenderer}의 책임).
 */
public record GenerationBlueprint(
        GenerationContext context,
        List<FileBlueprint> files,
        List<ProcessorStep> processors,
        List<GenerationWarning> warnings
) {
    public GenerationBlueprint {
        files = files == null ? List.of() : List.copyOf(files);
        processors = processors == null ? List.of() : List.copyOf(processors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        Set<Path> targets = new HashSet<>();
        for (FileBlueprint file : files) {
            if (!targets.add(file.targetPath())) {
                throw new IllegalArgumentException(
                        "GenerationBlueprint에 동일 targetPath를 중복 계획할 수 없습니다: "
                                + file.targetPath());
            }
        }
    }

    /** Scope Manifest가 사용할 현재 생성 계획의 결정적 파일 목록. */
    public List<Path> plannedTargetPaths() {
        return files.stream().map(FileBlueprint::targetPath).toList();
    }
}
