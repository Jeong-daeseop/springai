# CRUD 생성 Scope·Ownership·Revision 체인 연결 설계

> 작성일: 2026-08-24
> 관련 문서: `docs/figma/29_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_Specification.md`,
> `docs/figma/30_5Axis_Benchmark_Based_Pipeline_Evolution_Implementation_List.md`
> 활성화 게이트: `app.pipeline-evolution.mode` — `V2_PREVIEW` 이상(`usesV2Preview()==true`)에서만 동작

## 배경

12번 다이어그램(Pipeline Evolution)의 모드 전환표는 `V2_PREVIEW` 단계에서 "Scope·Ownership·Revision
불일치를 fail-closed 한다"고 명시한다. 실제 코드를 추적한 결과:

- `GenerationScopeManifest`·`GenerationOwnershipManifest`·`OwnershipConflictDetector`·
  `OwnershipRegionClassifier`·`OwnershipRegionHashService`·`GeneratedRegionPreservationService`·
  `SemanticMergePlanService`·`ApprovedWriteConflictGuard` — 8개 클래스 모두 "3-way 비교 결과가 주어졌을
  때 어떻게 판정할지"만 구현되어 있고, 그 비교 결과 자체를 만드는 데 필요한 부분이 없다.
- `CodeServiceGenerationExecutor`(CRUD 생성의 유일한 파일 저장 경로)는 기존 파일(Current)을 읽지
  않고 새로 렌더링한 내용으로 그냥 덮어쓴다.
- `GenerationHistoryService`는 요약 문자열만 저장하고 파일 전체 내용 스냅샷(Base)을 남기지 않는다.
- 파일 내부를 Generated/Binding/Protected/Unknown Region으로 나누는 마커 컨벤션이 어디에도 없다.

한편 "Revision"(파일 단위 drift 감지) 자체는 `ProjectWritePolicy.ATOMIC_APPROVED` +
`FileSystemApprovedProjectWritePort`로 이미 구현되어 있고 Thymeleaf Apply 경로가 쓰고 있다. CRUD
생성 경로는 `ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY`(enum 자신의 문서가 "신규 Generation
경로의 기본값이 되어서는 안 된다"고 명시)를 쓰고 있어 이 보호를 받지 못한다.

이 설계는 위 세 축을 실제 CRUD 생성 Apply 경로에 연결하는 방법을 다룬다.

## 목표

- Region 단위(Generated/Binding/Protected/Unknown)로 "생성기가 다시 만들려는 내용"과 "사람이 실제로
  고친 내용"을 구분해, 사람이 고친 부분은 자동 보존하고 충돌은 사람 검토로 넘긴다.
- 파일 단위 동시 수정 경합(Revision)은 기존 `ATOMIC_APPROVED` 인프라를 그대로 재사용해 막는다.
- 이 보호는 `V2_PREVIEW` 이상에서만 켜지고, 현재 운영 기본값은 `V2_APPLY`다. 장애·롤백 시
  `DISABLED`/`OBSERVE`/`DUAL_READ`를 명시하면 기존 동작으로 단계적으로 낮출 수 있다.

## 비목표

- Region 마커를 Java/JSP/HTML/XML 4개 파일 유형 모두에 실제로 심는 FreeMarker 템플릿 수정 작업의
  세부 diff는 이 설계 문서의 범위가 아니다(구현 계획 단계에서 레이어별로 진행).
- Figma/UiDesignSpecV2 쪽 Scope·Ownership(디자인 산출물 자체의 참조 무결성)은 이미 별도로 구현된
  `DesignContextArtifactReferenceValidator`/`RequiredComponentMappingApplyGate` 영역이며 이 설계의
  대상이 아니다 — 이 설계는 "생성된 소스 파일"의 Region 보호에 한정한다.

## 아키텍처 개요

```
CodeServiceGenerationExecutor.execute(RenderedGenerationPlan plan)
  if (!pipelineEvolutionProperties.usesV2Preview()) → 기존 legacyExecute() 그대로 (변경 없음)

  else:
    1. operationId = sha256(canonical(outputPath)|tableName|viewType)
         — outputPath는 절대경로로 정규화(Path.normalize().toAbsolutePath()) 후 해시한다.
    2. base = CrudGenerationSnapshotStore.findLatest(operationId)  // 없으면 빈 Manifest 취급
    3. 렌더 대상 파일마다:
         current = 디스크에 파일 있으면 읽음 (없으면 null)
         currentRegions = RegionMarkerParser.parse(current)
         newRegions     = RegionMarkerParser.parse(file.source())
         baseRegions    = base.regionsFor(relativePath)
         regionId 합집합 기준 ThreeWayRegionComparison 계산
         regionType 결정: New에 해당 regionId가 있으면 New의 type, 없으면(Region이 삭제됨) Base의
                          type — Current의 type은 참고하지 않는다(사람이 마커 자체를 지웠다고
                          해서 보호 등급이 내려가면 안 되므로).
         New에는 없는데 Base의 regionType이 PROTECTED 또는 BINDING이면, compare() 결과와 무관하게
                          강제로 REVIEW_CONFLICT로 승격한다 — "보호 대상 Region이 통째로 사라짐"은
                          NEW_ONLY(자동 삭제 허용)로 처리하지 않는다. GENERATED Region이 사라지는
                          것은 정상 흐름이므로 그대로 둔다.
    4. OwnershipConflictDetector.detect() → SemanticMergePlanService.preview()
    5. ApprovedWriteConflictGuard.requireApplyAllowed(plan)
         BOTH_CHANGED 있으면 → 이번 Apply 전체 중단(파일 하나도 안 씀), GenerationFailure로 보고
    6. PRESERVE 대상 Region을 New 콘텐츠에서 Current 내용으로 스플라이스
    7. Files.createDirectories(outputRoot) 직접 보장 (ATOMIC_APPROVED는 root 자동 생성 안 함)
    8. ProjectChangeSet 구성 — beforeHash = 실제 Current hash, policy = ATOMIC_APPROVED
    9. writePort.apply(changeSet)
         CONFLICT(동시 수정 경합) → 별도 실패 사유로 보고, 스냅샷 갱신 안 함
         APPLIED → CrudGenerationSnapshotStore.save(operationId, 새 GenerationOwnershipManifest)
```

핵심 원칙: **"막는다"가 아니라 "충돌 나는 부분만 사람 검토로 넘기고 나머지는 자동 병합해서 계속
진행한다."** `BOTH_CHANGED`만 진짜 충돌(REVIEW_CONFLICT)이고, `CURRENT_ONLY`(사람만 고침)는 자동
보존, `NEW_ONLY`(생성기만 바뀜)는 자동 반영된다.

## Scope 축

`GenerationScopeManifest`는 Ownership·Revision과 달리 "과거 대비 무엇이 바뀌었는지"가 아니라
"이번 생성 호출 하나가 정확히 무엇을 건드리기로 되어 있는지"를 고정하는 자기 일관성 검증이다.

- `rootArtifacts`: `GenerationBlueprint.plannedTargetPaths()`(Planner가 이미 결정한 파일 목록)를
  `VersionedArtifactReference`로 변환한 것 — `artifactType="GENERATED_SOURCE_FILE"`,
  `schemaVersion="1.0"`, `contentHash`는 New 렌더 결과의 sha256, `artifactId`는 상대경로.
- `dependencyArtifacts`/`validationOnlyArtifacts`: v1 범위에서는 빈 리스트로 둔다(CRUD 생성은
  레이아웃·CSS를 패치만 하지 완전히 재작성하지 않으므로, 이번 스펙에서는 "쓰기 대상이 아닌 것까지
  분류"하는 정교함은 다루지 않는다 — 필요해지면 후속 과제).
- `selectionReason`: `"CRUD 표준 생성 — {tableName} 단일 화면 세트"` 같은 고정 설명.
- 과거 이력과 비교하지 않으므로 스냅샷 저장소에 영속화하지 않는다.

**중요한 정정(구현 계획 자체 검토 중 발견)**: `CodeServiceGenerationExecutor.execute(RenderedGenerationPlan
plan)` 시점에는 실제 쓰기 대상(`toApply`)과 `rootArtifacts`가 **같은 `plan.files()`에서, 같은
메서드 호출 안에서** 파생된다. 이 둘을 비교하는 것은 "항상 참인 assertion"이 아니라 **회귀 방지
가치조차 없는 동어반복**이다 — 같은 데이터에서 같은 순간에 파생된 두 값은 그 사이 어떤 코드
변경이 일어나도 서로 달라질 수 있는 경로 자체가 없기 때문에, 이 비교는 미래의 어떤 버그도 잡아낼
수 없다. 따라서 **이번 구현에서는 `GenerationScopeManifest`를 실제로 만들거나 비교하는 코드를
추가하지 않는다.** `GenerationScopeManifest` 클래스 자체는 그대로 존재하되 미사용으로 남으며,
여러 화면을 한 번에 다루는 batch 생성이나 크로스-파일 의존성 검증처럼 "계획 시점"과 "실행 시점"이
서로 다른 소스에서 파생되는 시나리오가 생기면 그때 실제 가치가 생긴다.

## Region 마커 컨벤션

파일 유형별 주석 문법을 그대로 쓰고, 감싸는 기호와 무관하게 하나의 정규식(`RegionMarkerParser`)으로
파싱한다: `@region:{type}:{id} start` / `@region:{type}:{id} end`.

| 파일 유형 | 시작 | 종료 |
|---|---|---|
| Java | `// @region:{type}:{id} start` | `// @region:{type}:{id} end` |
| Thymeleaf HTML | `<!-- @region:{type}:{id} start -->` | `<!-- @region:{type}:{id} end -->` |
| JSP | `<%-- @region:{type}:{id} start --%>` | `<%-- @region:{type}:{id} end --%>` |
| MyBatis Mapper XML | `<!-- @region:{type}:{id} start -->` | `<!-- @region:{type}:{id} end -->` |

`{type}`은 `OwnershipRegionClassifier`가 이미 인식하는 접두사(`generated`/`binding`/`protected`)와
맞춘다. `{id}`는 파일 내에서만 고유하면 된다.

**RegionType → MergePolicy 기본 매핑**

| RegionType | 의미 | MergePolicy |
|---|---|---|
| `GENERATED` | 생성기가 전적으로 소유 | `REGENERATE` — 항상 New로 덮어씀 |
| `BINDING` | 스키마·계약에 자동 종속 | `CONTRACT_ONLY` — 계약이 바뀔 때만 갱신 |
| `PROTECTED` | 사람이 의도적으로 커스터마이징 | `PRESERVE` — Current 유지 |
| `UNKNOWN`(마커 오류·미분류) | 안전하게 판단 불가 | `REVIEW_CONFLICT` — fail-safe |

**마커 없는 파일/구간**: 전체를 `regionId="generated.file"`, `RegionType.GENERATED` 단일 Region으로
취급한다(레거시 파일 호환).

**마커 검증 실패(짝 안 맞음·id 중복)**: 그 파일 전체를 `UNKNOWN`으로 강등해 `REVIEW_CONFLICT`로
fail-safe 처리한다 — 조용히 무시하지 않는다.

**6개 레이어 초기 마커 배치**

| 레이어 | 배치 |
|---|---|
| VO | 전체 `generated` |
| Mapper XML | resultMap·기본 CRUD SQL은 `binding`, 나머지는 `generated` |
| Controller | 표준 CRUD 메서드는 `generated`, 파일 하단 커스텀 액션 영역은 `protected` |
| Service(interface) | 전체 `generated` |
| ServiceImpl | 표준 CRUD 메서드 본문은 `generated`, 비즈니스 로직 자리는 `protected` |
| View(JSP/HTML) | 표준 목록/상세/폼은 `generated`, 하단 커스텀 영역은 `protected` |

## Base 스냅샷 저장소

기존 `ThymeleafProjectOperationRepository` 패턴(Flyway 테이블 생성, JSON 컬럼에 전체 스냅샷
직렬화, `PRIMARY KEY(OPERATION_ID, REVISION)`로 별도 lock 없는 compare-and-set)을 그대로 따른다.

```sql
CREATE TABLE AI_CRUD_GENERATION_SNAPSHOT (
    OPERATION_ID   VARCHAR(64)  NOT NULL,   -- sha256(canonical(outputPath)|tableName|viewType)
    REVISION       INT          NOT NULL,
    SNAPSHOT_JSON  LONGTEXT     NOT NULL,   -- GenerationOwnershipManifest 직렬화
    CREATED_AT     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (OPERATION_ID, REVISION)
)
```

새 DTO를 만들지 않고 `GenerationOwnershipManifest`를 그대로 저장한다 — 이번에 실제로 쓴 New
콘텐츠로 만든 Manifest가 곧 다음 번 Base가 된다.

```java
public interface CrudGenerationSnapshotStore {
    Optional<GenerationOwnershipManifest> findLatest(String operationId);
    void save(String operationId, GenerationOwnershipManifest manifest); // revision 자동 +1
}
```

`save()`는 `writePort.apply()`가 `APPLIED`를 반환한 직후에만 호출한다(Thymeleaf 쪽 "apply 성공
직후에만 색인한다" 규약과 동일) — 실패한 시도가 다음 Base를 오염시키지 않도록.

## 부트스트랩(기존 화면 채택)

이 기능을 켜기 전 이미 생성돼 있던 화면은 스냅샷이 없다. 그 상태로 재생성하면 Base가 없어 Current와
New가 조금이라도 다르면 의도적으로 `BOTH_CHANGED`(충돌)로 판정된다 — "신뢰할 Base가 없으면 사람에게
확인받는다"는 fail-safe다.

신규 MCP Tool `adoptCurrentAsBaseline(database, tableName, viewType)`을 추가한다:

- 대상 화면의 `FileBlueprint` 목록(파일을 렌더링·저장하지 않고 계획만 조회)을 얻는다.
- 디스크의 Current 파일들을 그대로 읽어 `RegionMarkerParser`로 분할하고, 비교 없이 그대로
  `GenerationOwnershipManifest`를 만들어 `CrudGenerationSnapshotStore.save()`한다.
- 파일을 전혀 쓰지 않는다 — "지금 디스크에 있는 내용을 신뢰하고 다음 Base로 등록"하는 것뿐이다.
- 다음 재생성부터는 이 채택된 Base 기준으로 정상적인 3-way 비교가 이뤄진다.

## Revision 축(파일 단위) 전환

`CodeServiceGenerationExecutor`가 (`usesV2Preview()`일 때) `ProjectWritePolicy.ATOMIC_APPROVED` +
실제 Current hash를 `beforeHash`로 채워 호출한다.

**주의**: `ATOMIC_APPROVED`는 `BEST_EFFORT_COMPATIBILITY`와 달리 출력 루트 디렉터리를 자동
생성하지 않는다(신규 프로젝트 최초 생성 지원은 `BEST_EFFORT` 전용 완화였다). 그래서 Executor가
정책 전환과 별개로 `Files.createDirectories(outputRoot)`를 직접 먼저 호출해 이 차이를 흡수한다 —
`FileSystemApprovedProjectWritePort` 자체는 다른 호출자(Thymeleaf)를 위해 그대로 둔다.

## 실패 시 동작

- Region `BOTH_CHANGED` 충돌 → `writePort.apply()` 호출 전에 이미 막힘. 파일을 하나도 쓰지 않고
  `GenerationFailure("ownership-guard", ...충돌 Region 목록...)`로 보고한다. "전부 아니면 전무" —
  `RequiredComponentMappingApplyGate`가 이미 쓰는 것과 같은 원칙이다.
- Base에는 있는데 New 템플릿에서 해당 Region 자체가 사라졌고 그 Region이 `PROTECTED`/`BINDING`인
  경우(스플라이스할 자리가 없음) → 아키텍처 개요 4단계의 강제 승격 규칙에 따라 조용히 버리지 않고
  `REVIEW_CONFLICT`로 처리한다. `GENERATED` Region이 사라지는 것은 정상 흐름이라 그대로 둔다.
- 파일 단위 동시 수정 경합(`ATOMIC_APPROVED`의 `CONFLICT`) → Region 충돌과는 별도 실패 사유
  (`GenerationFailure("write-guard", ...)`)로 보고하고, 스냅샷을 갱신하지 않는다.

## 테스트 계획

- 신규 단위 테스트: `RegionMarkerParser`(4개 마커 문법·중복 id·짝 안 맞음 fail-safe),
  `CrudGenerationSnapshotStore` JDBC 구현(`ThymeleafProjectOperationRepository` 테스트 패턴 재사용).
- `CodeServiceGenerationExecutor` 통합 시나리오:
  1. 최초 생성(Base·Current 없음) → 충돌 없음, 스냅샷 최초 색인
  2. 재생성인데 아무것도 안 바뀜 → 충돌 없음
  3. 생성기만 바뀜(`NEW_ONLY`) → 자동 반영
  4. 사람만 고침(`CURRENT_ONLY`) → 자동 보존, New에 Current 내용 스플라이스 확인
  5. 둘 다 바뀜(`BOTH_CHANGED`) → Apply 전체 중단, 파일 하나도 안 씀 확인
  6. 동시 수정 경합 → `ATOMIC_APPROVED`의 `CONFLICT`로 별도 실패 사유 확인
  7. `adoptCurrentAsBaseline` → 파일 미변경, 스냅샷만 생성, 이후 재생성이 그 Base로 비교됨
  8. `usesV2Preview()==false`(명시적으로 `DISABLED`/`OBSERVE`/`DUAL_READ`로 낮춘 경우) → 기존
     `BEST_EFFORT_COMPATIBILITY` 경로 그대로, 회귀 없음 확인

## 롤아웃 순서

1. `RegionMarkerParser` + `CrudGenerationSnapshotStore`(Flyway 테이블 포함) 구현·단위 테스트
2. `CodeServiceGenerationExecutor`에 섹션 "아키텍처 개요"의 분기 연결(`usesV2Preview()` 게이트 뒤)
3. 6개 레이어 FreeMarker 템플릿에 마커 삽입
4. `adoptCurrentAsBaseline` MCP Tool 추가 + `McpConfig` 등록
5. 통합 테스트 전체 통과 후, `app.pipeline-evolution.mode` 기본값을 `V2_APPLY`로 전환한다.
   운영 장애·롤백 시에는 `V2_PREVIEW` 또는 `DUAL_READ`를 명시한다.
