# CRUD 소스 생성 KRDS 원본 자산 검증 — 구현명세서 및 구현목록

> [`CRUD_생성_KRDS_자산_검증_구현제안.md`](./CRUD_생성_KRDS_자산_검증_구현제안.md)의 제안을 실제
> 코드 시그니처(`GenerationStageProcessor`, `ProcessorResult`, `GenerationContext`,
> `CrudGenerationPlanner.processorSteps()`)에 맞춰 구체화한 구현명세서 + 구현목록이다.
> **구현 여부는 결정되지 않았으며, 승인 전까지는 이 문서에 따라 코드를 변경하지 않는다.**

---

## 1. 배경 및 목적

[`CRUD_생성_KRDS_자산_미검증_검토.md`](./CRUD_생성_KRDS_자산_미검증_검토.md)에서 확인한 대로, CRUD
auto 경로는 `styles.css`만 self-healing되고 `_ds_bundle.css`/`krds.min.js`는 미검증이며, claude
경로는 검증 자체가 없다. 이 문서는 auto 경로에 실제로 적용 가능한 구현 명세를 정리한다.

### 설계 원칙

- **기존 확장점 그대로 재사용**: `CrudFormColumnCssProcessor`/`CrudTableDensityCssProcessor`와
  완전히 동일한 `GenerationStageProcessor` 구현 패턴을 따른다. 새 인터페이스·새 파이프라인을
  만들지 않는다.
- **실행 순서**: `CrudGenerationPlanner.processorSteps()`(358-372행)에서 `CrudTableDensityCssProcessor`
  가 `order=100`, `CrudFormColumnCssProcessor`가 `order=110`이다. 자산이 없는데 CSS 보강부터 시도할
  이유가 없으므로, 신규 프로세서는 **`order=90`**으로 이 둘보다 먼저 실행되게 한다.
- **차단 정책**: 기존 두 CSS 프로세서와 동일하게 `FailurePolicy.STOP`을 쓴다 — 자산이 없으면
  생성을 그 자리에서 멈춘다(조용한 완주 방지, 17.1 해결안 B와 같은 원칙).

---

## 2. 목표 아키텍처

```
CrudGenerationApplicationService(auto, GenerateCrudProjectUseCase 구현체)
  └─ GenerationProcessorRunner가 PRE_WRITE stage의 ProcessorStep들을 order 순으로 실행
       1. KrdsAssetVerificationProcessor   [신규, order=90,  FailurePolicy.STOP]
            └─ WAR: src/main/webapp/resources/{css/_ds_bundle.css, js/krds.min.js}
               Boot: src/main/resources/static/resources/{...}
               중 하나라도 완비 → ProcessorResult.ok()
               둘 다 미완비 → ProcessorResult.failed("KRDS 원본 자산 없음", ...)
                    └─ FailurePolicy.STOP → 파이프라인 중단, 이후 Processor 미실행
       2. CrudTableDensityCssProcessor     [기존, order=100, 변경 없음]
       3. CrudFormColumnCssProcessor       [기존, order=110, 변경 없음]
       ...
```

claude 경로(`CrudPromptBuilderService`)는 이 파이프라인을 타지 않으므로 별도 처리가 필요하다 —
§6 참고(이번 Phase 범위 밖, `CRUD_생성_KRDS_자산_검증_구현제안.md` §4.2의 구조적 한계 그대로 적용).

---

## 3. 데이터/인터페이스 설계

### 3.1 `GenerationStageProcessor` 구현 (실제 시그니처 확인 완료)

```java
public interface GenerationStageProcessor {
    String id();
    GenerationStage stage();
    boolean supports(GenerationContext context);
    ProcessorResult process(GenerationProcessingContext context);
}
```

`GenerationContext.outputPath()`는 `String`이다(`Path`가 아님) — 검증 로직에서
`Path.of(context.outputPath())`로 변환해야 한다.

### 3.2 신규 클래스 `KrdsAssetVerificationProcessor`

**위치**: `service/generation/crud/KrdsAssetVerificationProcessor.java` (신규)

```java
package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * KRDS 원본 자산(_ds_bundle.css/krds.min.js) 존재를 CRUD 생성 전에 검증한다.
 * CrudTableDensityCssProcessor/CrudFormColumnCssProcessor보다 먼저 실행되어(order=90),
 * 자산이 없는 프로젝트에서 CSS 보강을 시도하기 전에 명확히 차단한다.
 */
@Component
public class KrdsAssetVerificationProcessor implements GenerationStageProcessor {

    static final String ID = "krdsAssetVerificationProcessor";

    private static final String[] WAR_PATHS = {
            "src/main/webapp/resources/css/_ds_bundle.css",
            "src/main/webapp/resources/js/krds.min.js"
    };
    private static final String[] BOOT_PATHS = {
            "src/main/resources/static/resources/css/_ds_bundle.css",
            "src/main/resources/static/resources/js/krds.min.js"
    };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.PRE_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        return true; // CRUD 템플릿은 항상 krds-btn 등을 하드코딩하므로 항상 검증
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        Path root = Path.of(context.context().outputPath());
        if (allExist(root, WAR_PATHS) || allExist(root, BOOT_PATHS)) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed("KRDS 원본 자산 없음",
                List.of(new GenerationFailure(ID,
                        "_ds_bundle.css/krds.min.js가 없습니다 — "
                                + "ProjectInitializrTool.initializeProject()를 먼저 실행하세요.")));
    }

    private boolean allExist(Path root, String[] relativePaths) {
        for (String relative : relativePaths) {
            if (!Files.exists(root.resolve(relative))) return false;
        }
        return true;
    }
}
```

`supports()`가 항상 `true`인 이유: `Thymeleaf_레거시전환_KRDS_반영_검토.md`에서 검증된 대로 CRUD
템플릿(`CrudTemplateRenderer`)은 화면 종류와 무관하게 항상 `krds-btn`/`egov-*` 클래스를 하드코딩
한다(6번 다이어그램에서 확인됨) — 17.1의 `KRDS_CLASS_PATTERN` 같은 조건부 탐지가 CRUD에는
불필요하다.

### 3.3 `CrudGenerationPlanner.processorSteps()` 수정

**위치**: `service/generation/crud/CrudGenerationPlanner.java:358-372`

```java
private static List<ProcessorStep> processorSteps() {
    return List.of(
            new ProcessorStep(KrdsAssetVerificationProcessor.ID,
                    GenerationStage.PRE_WRITE, 90, FailurePolicy.STOP),   // ← 신규
            new ProcessorStep(CrudTableDensityCssProcessor.ID,
                    GenerationStage.PRE_WRITE, 100, FailurePolicy.STOP),
            new ProcessorStep(CrudFormColumnCssProcessor.ID,
                    GenerationStage.PRE_WRITE, 110, FailurePolicy.STOP),
            new ProcessorStep(CrudEntryPointProcessor.ID,
                    GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
            new ProcessorStep(SharedProcessorIds.THYMELEAF_RUNTIME,
                    GenerationStage.POST_WRITE, 200, FailurePolicy.CONTINUE),
            new ProcessorStep(SharedProcessorIds.CONTROLLER_SCAN,
                    GenerationStage.POST_WRITE, 210, FailurePolicy.CONTINUE),
            new ProcessorStep(SharedProcessorIds.MYBATIS_RUNTIME,
                    GenerationStage.POST_WRITE, 300, FailurePolicy.CONTINUE));
}
```

한 줄만 추가되며 기존 6개 스텝의 순서·정책은 변경하지 않는다.

---

## 4. 신규/수정 파일 목록

| 파일 | 변경 유형 | 내용 |
|---|---|---|
| `service/generation/crud/KrdsAssetVerificationProcessor.java` | 신규 | §3.2 |
| `service/generation/crud/CrudGenerationPlanner.java` | 수정 | `processorSteps()`에 한 줄 추가(§3.3) |
| 관련 단위/통합 테스트 | 신규 | §7 |

claude 경로(`CrudPromptBuilderService`) 변경은 이번 Phase 범위에서 제외한다 — §6 참고.

---

## 5. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| `order=90`이 다른 Processor와 충돌 | 실행 순서 꼬임 | `processorSteps()`는 이 프로젝트 전역에서 CRUD 전용 리스트라 충돌 없음(확인됨, 358-372행 전체가 CRUD만의 6개 스텝) |
| `FailurePolicy.STOP`이라 기존에 자산 없이도 "동작하던" 환경이 있다면 그 환경에서 회귀 발생 | 이미 자산 없이 CRUD를 생성해 온 사용자가 있다면 이 배포 이후 실패로 바뀜 | 이건 의도된 동작(원래 스타일이 깨진 채였던 걸 명시적 에러로 바꾸는 것) — `Thymeleaf_레거시전환_KRDS_반영_검토.md` 해결안 B와 동일한 성격의 트레이드오프로, 별도 완화책 불필요 |
| `supports()`를 항상 `true`로 고정 | 예외적으로 KRDS를 안 쓰는 CRUD 변형이 생기면 오탐 | 현재 코드베이스에 그런 변형 없음(확인됨). 생기면 그때 `supports()`에 조건 추가 |

---

## 6. 1차 구현 제외 범위

- **claude 경로 프롬프트 경고**(`CrudPromptBuilderService`): `CRUD_생성_KRDS_자산_검증_구현제안.md`
  §4에서 이미 검토했듯 별도 설계가 필요하다 — auto와 판정 로직을 공유하려면
  `KrdsAssetVerificationProcessor`의 `allExist()`를 별도 유틸로 추출해야 하는데, 이건 auto 단독
  구현보다 범위가 커서 이번 Phase에서 제외하고 후속 Phase로 미룬다.
- `saveGeneratedCode()` 저장 시점 검증: 오탐 위험으로 별도 검토 필요(제안 문서 §6과 동일).
- `styles.css` self-healing 로직 변경: 이미 존재하며 이번 범위와 무관.

---

## 7. 단계별 구현목록

### Phase 1 — 신규 Processor (필수)

| 순서 | 작업 |
|---|---|
| 1 | `KrdsAssetVerificationProcessor` 작성(§3.2) |
| 2 | `CrudGenerationPlanner.processorSteps()`에 등록(§3.3) |

### Phase 2 — 테스트 (필수)

| 순서 | 작업 |
|---|---|
| 3 | 자산 없는 fixture 프로젝트로 CRUD 생성 시도 → `ProcessorResult.failed` 및 파이프라인 중단 확인 |
| 4 | WAR 경로만 자산 있는 fixture → 통과 확인 |
| 5 | Boot 경로만 자산 있는 fixture → 통과 확인 |
| 6 | 자산 있는 기존 fixture로 기존 CRUD 생성 테스트 회귀 없음 확인 |

### Phase 3 — 검증

| 순서 | 작업 |
|---|---|
| 7 | `./gradlew build` 전체 통과 확인 |

---

## 8. 검증 방법

1. `_ds_bundle.css`/`krds.min.js` 없는 `@TempDir` fixture에 `buildFullCrudPrompt(..., llmProvider="auto", ...)` 호출 → 생성 실패, `KrdsAssetVerificationProcessor`의 실패 메시지 확인
2. 자산 있는 기존 fixture에서 기존 CRUD 생성 테스트 스위트 전체 통과 확인(회귀 없음)
3. `./gradlew test --tests "*Crud*"` 및 `./gradlew build` 전체 통과 확인

---

## 9. 관련 문서

- [`CRUD_생성_KRDS_자산_검증_구현제안.md`](./CRUD_생성_KRDS_자산_검증_구현제안.md) — 이 명세의 원 제안(auto+claude 개요, claude 경로 구조적 한계)
- [`CRUD_생성_KRDS_자산_미검증_검토.md`](./CRUD_생성_KRDS_자산_미검증_검토.md) — 문제 원 검토
- [`Thymeleaf_레거시전환_KRDS_반영_검토.md`](./Thymeleaf_레거시전환_KRDS_반영_검토.md) — 17.1의 동일 문제 해결안 B(구현 완료, 커밋 `65e0ab2`)
- `service/generation/crud/CrudFormColumnCssProcessor.java` — 구현 패턴 원본
- `service/generation/crud/CrudGenerationPlanner.java:358-372` — 등록 지점
