# V2_APPLY 모드에서 "화면(디자인) 제시 + 구현 요청" 처리 방안 검토

> 작성일: 2026-09-04
> 대상: `buildFullCrudPrompt`(Thymeleaf) 생성 경로와 `app.pipeline-evolution.mode=V2_APPLY` 게이트
> 결론: **서버를 `V2_PREVIEW`로 두고 디자인은 `designReferenceId`/`screenSpecificationId`로 넘긴다. `V2_APPLY` 정공법은 MCP 서버 미완성 배선이 선행돼야 한다.**

---

## 1. 배경 / 증상

신규 eGov 프로젝트에서 아래 순서로 진행 중 CRUD 생성이 실패했다.

```
initializeProject(... viewType="jsp", designSystemProfileId="krds")   # 성공
generateThymeleafLayout(outputPath=..., packageName=...)              # 성공 (layout 5종 + GNB 컴포넌트)
buildFullCrudPrompt(database="ebt", tableName="LETTNEMPLYRINFO",
                    domain="Employer", viewType="thymeleaf", llmProvider="auto")  # 실패
```

실패 메시지:

```
=== [auto] eGovFrame 5.x CRUD 소스 생성 실패 ===
[코드 검증 결과]
V2_APPLY 모드에서는 Thymeleaf 생성 전 Figma 디자인 참조로 승인된 화면명세가 필요합니다
  1. analyzeFigmaReference(figmaUrl, nodeId, featureType="crud") 호출 → 분석 ID 획득
  2. createScreenSpecification(...) 호출 — APPROVED면 바로 사용, REVIEW_REQUIRED면 revise 후 approve
  3. buildFullCrudPrompt(..., screenSpecificationId=승인된 화면명세 ID)로 다시 호출
총 0개 성공, 3개 실패
```

`designSystemProfileId` 유무, 생성된 프로젝트 파일과 **무관**하다. `buildFullCrudPrompt`에 디자인 파라미터(`designReferenceId`/`screenSpecificationId`)를 주지 않은 것이 직접 원인이지만, **디자인 파라미터를 채워도 V2_APPLY에서는 뒤쪽 게이트에서 다시 막힌다**(§3).

---

## 2. 근본 원인 — 서버 전역 플래그

현재 붙어 있는 springai MCP 서버가 `V2_APPLY` 모드로 기동돼 있다.

`src/main/resources/application.yaml:118-126`
```yaml
app:
  # 5축 파이프라인은 신규 Apply(V2_APPLY)를 기본 활성화한다. 장애·롤백 시
  # APP_PIPELINE_EVOLUTION_MODE=V2_PREVIEW 또는 DUAL_READ를 명시해 단계적으로 낮출 수 있다.
  pipeline-evolution:
    mode: ${APP_PIPELINE_EVOLUTION_MODE:V2_APPLY}
```

`PipelineEvolutionProperties.java`
```java
public boolean usesV2Apply()      { return mode == Mode.V2_APPLY; }
public boolean usesV2Preview()    { return mode == Mode.V2_PREVIEW || mode == Mode.V2_APPLY; }
public boolean readsV2Artifacts() { return mode == Mode.DUAL_READ || mode == Mode.V2_PREVIEW || mode == Mode.V2_APPLY; }
```

`Mode` 단계: `DISABLED → OBSERVE → DUAL_READ → V2_PREVIEW → V2_APPLY`

관련 커밋 (기본값 전환·게이트 추가가 최근에 일괄 진입):

```
8445977 feat: default pipeline-evolution.mode to V2_APPLY
f9d811c feat: require approved screen specification for V2_APPLY Thymeleaf generation
5b8244a feat: add conditional approval gate for high-risk CRUD tables
7d42817 feat: wire PipelineMigrationGuard/LegacyCompatibilityService and plan Ownership guard
```

---

## 3. V2_APPLY에서 `buildFullCrudPrompt`(Thymeleaf)가 통과해야 하는 게이트

| # | 위치 | 조건 | 디자인 제시 시 |
|---|------|------|----------------|
| 1 | `CrudGenerationPlanner.java:188` | `viewType==THYMELEAF && usesV2Apply() && isBlank(designReferenceId) && isBlank(screenSpecificationId)` → 즉시 reject (DB 조회 전) | `designReferenceId`/`screenSpecificationId` 있으면 **통과** |
| 2 | `GenerationDesignContextService.java:69-88` | V2_APPLY(`mandatory=true`)에서는 화면명세의 `uiDesignSpecReference != null` 이어야 함. null이면 `DESIGN_EVIDENCE_MISSING` | ❌ **통과 불가** (§3.1) |
| 3 | `RequiredComponentMappingApplyGate.java:43-96` (`CrudGenerationPlanner.java:267-278`) | UiDesignSpec의 각 `componentRef`마다 `(logicalType, componentSetKey, "thymeleaf-krds")` 승인 `DesignCodeComponentMapping`이 repository에 있어야 함. 없으면 `승인 Mapping 누락` | DB 시드 없으면 실패 |
| 3.5 | `CrudGenerationPlanner.java:283-296` | `RendererProfileLoader.loadApproved("thymeleaf-krds", version)` 성공 + Command의 RendererProfile 참조와 hash 일치 | 승인 프로파일 필요 |

### 3.1 게이트 2가 구조적으로 막히는 이유 (핵심)

`GenerationDesignContextService.resolve()` — `buildFullCrudPrompt` 내부에서 호출 —
`src/main/java/com/krdevops/springai/service/GenerationDesignContextService.java:38-66`

```java
if (screenSpecificationId != null && !screenSpecificationId.isBlank()) {
    specification = screenSpecificationService.get(screenSpecificationId);
} else if (designReferenceId != null && !designReferenceId.isBlank()) {
    DesignAnalysisResult analysis = designAnalysisService.get(designReferenceId);
    specification = screenSpecificationService.create(               // ← v1 create
            database, tableName, screenName, featureType, analysis.uiSpec());
}
...
private void validateArtifactReferences(ScreenSpecification specification) {
    boolean mandatory = pipelineEvolutionProperties.usesV2Apply();   // V2_APPLY = true
    ...
    if (specification.uiDesignSpecReference() == null) {
        if (mandatory) {
            throw new DesignContextArtifactReferenceValidator.DesignContextArtifactException(
                PipelineEvolutionErrorCode.DESIGN_EVIDENCE_MISSING,
                "V2 Apply에는 UiDesignSpec Artifact 참조가 필요합니다.");
        }
        return;  // V2_PREVIEW 이하: 조용히 통과
    }
    ...
}
```

`uiDesignSpecReference`를 채우는 유일한 경로는 `ScreenSpecificationService.createFromV2(...)`:

`src/main/java/com/krdevops/springai/service/ScreenSpecificationService.java:82-105`
```java
public ScreenSpecification createFromV2(..., UiDesignSpecV2 uiSpec, ...) {
    ...
    VersionedArtifactReference designRef = new VersionedArtifactReference(
            uiSpec.specId(), "UI_DESIGN_SPEC_V2", uiSpec.schemaVersion(),
            uiSpec.contentHash(), uiSpec.source().sourceRevision());
    specification = specification.withDesignContext(designRef, uiSpec.designSystemSnapshotRef(), ...);
    ...
}
```

그런데 **`createFromV2`는 프로덕션 코드에서 호출자가 없다:**

```
$ grep -rn 'createFromV2' --include='*.java' src/main/java/
ScreenSpecificationService.java:82   (정의)
ScreenSpecificationService.java:107  (오버로드 정의)
ScreenSpecificationService.java:110  (오버로드 → 본체 위임)
```

- MCP `createScreenSpecification` 도구(`DesignReferenceTool.java:74-92`)도 v1 `create(...)` 호출.
- `analyzeFigmaReference` / `analyzeDesignReference`는 `DesignAnalysisResult`를 반환하고, 여기서 꺼내는 `uiSpec()`은 **v1 `UiDesignSpec`**.

**결론:** MCP 도구로 만들 수 있는 모든 `ScreenSpecification`은 `uiDesignSpecReference == null`이다 → V2_APPLY에서 게이트 2에서 항상 `DESIGN_EVIDENCE_MISSING`. 즉 **현재 V2_APPLY + Thymeleaf + `buildFullCrudPrompt` 조합은 입력과 무관하게 성공할 수 없다.**

---

## 4. 처리 방안

### 방안 A — `V2_PREVIEW` + 디자인을 `screenSpecificationId`로 전달 (권장, 지금 동작)

```
1. analyzeDesignReference(referencePath)          # 로컬 PNG/JPG/이미지형 PDF
   또는 analyzeFigmaReference(figmaUrl, nodeId)   # Figma 단일 FRAME
     → analysisId

2. createScreenSpecification(database, tableName, screenName, featureType="crud",
                             designAnalysisId=analysisId,
                             listColumns=null, detailColumns=null)
     → APPROVED         : 단순 케이스(표준 단일 테이블) — 그대로 사용
     → REVIEW_REQUIRED  : reviseScreenSpecification(spec) → approveScreenSpecification(id)

3. buildFullCrudPrompt(database, tableName, domain, packageName, outputPath,
                       llmProvider="auto", viewType="thymeleaf", egovVersion="5.0",
                       screenSpecificationId=승인된_ID)
```

**`V2_PREVIEW`에서 게이트 동작:**

| 게이트 | V2_PREVIEW 동작 |
|--------|-----------------|
| 1 | `usesV2Apply()==false` → 조건 불성립, 건너뜀 |
| 2 | `mandatory=false`. `uiDesignSpecReference==null`이어도 `return` (예외 없음) |
| 3 | `if (!properties.usesV2Apply()) return List.of();` → 조기 리턴 |
| 3.5 | 레거시 fixture 경로(협력자 미주입)면 보존, 주입 시에만 검사 |

**디자인이 실제로 반영되는 범위** (V2_PREVIEW에서도 유효):

- 화면명세의 커스텀 라벨 → CRUD/Board list·detail 필드 라벨 (커밋 `e3ad1f3`)
- `listColumns` / `detailColumns` 선택 — 표시 컬럼, 복합 PK 항상 포함, PK 포함 최대 6개 (초과 시 예외)
- 필드 역할(제목/상태/작성자 등), 액션(검색/등록/삭제), archetype(CRUD_LIST 등)
- 표 밀도 `STANDARD` / `COMPACT` / `COMFORTABLE` → CRUD 목록 wrapper + `styles.css` 멱등 보강
- `PageSpec.selectionSource`: 명시 컬럼 `EXPLICIT`, 디자인 분석 선택 `DESIGN_REFERENCE`, 기본 `DEFAULT`

**반영되지 않는 것** (CLAUDE.md 명시):

- GNB/LNB 존재 여부·구조, 콘텐츠 폭, 색상·간격 토큰 → `generateThymeleafLayout()` 산출물 그대로 사용
- 픽셀 단위 재현, 회전·클리핑·비가시 레이어

**전제 조건:**

- 로컬 이미지/PDF: `app.design-vision.provider = openai` 또는 `ollama`
- Figma: `app.design-vision.figma.enabled=true` + `FIGMA_ACCESS_TOKEN` (기본 비활성, 서버 `127.0.0.1` 바인딩). `/file/...` `/design/...` URL + `node-id` 필수. 서버 PAT 권한 조회이므로 다중 사용자 배포 부적합.

### 방안 B — `V2_APPLY` 유지, 정공법 (springai 서버 개발 필요)

아래가 저장소에서 완성돼야 함:

1. **`createFromV2(...)` 배선** — `analyzeFigmaReference` → `UiDesignSpecV2` 생성·아티팩트 영속화 → `createScreenSpecification`(또는 신규 도구)이 `createFromV2`를 타서 `uiDesignSpecReference` 부여. (현재 v1 `UiDesignSpec`만 반환)
2. **`thymeleaf-krds` 컴포넌트 매핑 시드 + 승인** — `DesignCodeComponentMapping` 레코드. 관련 도구: `auditComponentRegistry`, `preflightComponentRegistry`, `createFigmaBundleFromApprovedSpecification` (어느 것이 승인 레코드를 실제 영속화하는지 확인 필요)
3. **`thymeleaf-krds` RendererProfile 승인 확인** — 리소스 `figma/contracts/renderer-profile-thymeleaf-krds-v1.json` 존재. `RendererProfileLoader.loadApproved(...)`가 통과하는지 확인
4. `app.design-vision.figma.enabled=true` + PAT

→ **eGov 프로젝트 작업 흐름에서 해결 불가. MCP 서버 백로그 항목.** 픽셀 단위 Figma 재현이 즉시 필수 요건이면 이 작업이 선행돼야 한다.

### 방안 C — `viewType="jsp"` + 수동 조정

- JSP는 게이트 1이 `viewType==THYMELEAF` 조건이라 건너뜀. 게이트 2·3도 Thymeleaf 한정.
- `app.crud-generation.approval-required-for-all=false` + `approval-required-tables=[]` (기본값)이면 JSP CRUD는 스키마 기반으로 바로 생성.
- 생성 후 목업에 맞춰 마크업 수작업. **방금 만든 Thymeleaf 공통 layout / KRDS 연동은 사용 못 함.**

---

## 5. 정리

| 상황 | 처리 |
|------|------|
| 디자인 없음 | 서버 `V2_PREVIEW` → `buildFullCrudPrompt`(스키마 기반) |
| **디자인 제시 + 구현 요청** | 서버 `V2_PREVIEW` → `analyzeDesignReference`/`analyzeFigmaReference` → `createScreenSpecification` → (`reviseScreenSpecification` →) `approveScreenSpecification` → `buildFullCrudPrompt(screenSpecificationId=...)` |
| 픽셀 재현이 필수 | 방안 B 선행 (MCP 서버 개발) |

**두 경우 모두 MCP 서버를 `V2_PREVIEW`로 내리면 해결된다.** `application.yaml` 주석이 명시한 공식 롤백 경로:

```
APP_PIPELINE_EVOLUTION_MODE=V2_PREVIEW   # 또는 DUAL_READ / OBSERVE / DISABLED
```

`V2_APPLY`는 게이트(`f9d811c`, `5b8244a`)와 기본값 전환(`8445977`)만 진입했고, 그 앞단(V2 디자인 명세 생성·컴포넌트 매핑 시드)은 CRUD 생성기에 아직 연결되지 않았다. 연결 전까지 `V2_APPLY`에서 Thymeleaf CRUD 자동 생성은 불가.

---

## 부록 — 확인에 사용한 근거

| 확인 항목 | 명령 / 파일 |
|-----------|-------------|
| 서버 모드 기본값 | `src/main/resources/application.yaml:122` |
| 게이트 1 (조기 차단) | `src/main/java/com/krdevops/springai/service/generation/crud/CrudGenerationPlanner.java:185-201` |
| 게이트 2 (아티팩트 참조) | `src/main/java/com/krdevops/springai/service/GenerationDesignContextService.java:69-92` |
| 게이트 3 (컴포넌트 매핑) | `src/main/java/com/krdevops/springai/service/designsystem/RequiredComponentMappingApplyGate.java:43-96` |
| `createFromV2` 미배선 | `grep -rn 'createFromV2' --include='*.java' src/main/java/` → 정의 3건, 호출 0건 |
| v1 `create`만 사용 | `ScreenSpecificationService.java:63-79`, `DesignReferenceTool.java:74-92` |
| 관련 커밋 | `git log --oneline --grep='V2_APPLY\|pipeline-evolution'` |
