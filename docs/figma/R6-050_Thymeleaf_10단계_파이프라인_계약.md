# R6-050 Thymeleaf 10단계 파이프라인 계약

> 계약 버전: `thymeleaf-generation-pipeline-v1`
> 확정일: 2026-08-17
> 구현 기준: `ThymeleafGenerationPipelineContract`

## 1. 목적

JSP·Controller·VO 분석부터 Thymeleaf 빌드·렌더 검증까지의 실행 순서, 단계별 입력·출력,
Issue 판정, 중단, 재시도 및 Hash 규칙을 하나의 폐쇄 계약으로 고정한다. 선언 순서를 바꾸거나
단계를 추가·삭제하는 변경은 계약 버전을 올려야 한다.

## 2. 고정 단계

| 순서 | 단계 | 입력 계약 | 출력 계약 | 재시도 정책 |
|---:|---|---|---|---|
| 1 | `SOURCE_ANALYSIS` | `LegacyConversionRequest` | `LegacyScreenAnalysis` | 입력 수정 후 |
| 2 | `BINDING_CONTRACT` | `LegacyScreenAnalysis` | `ThymeleafBindingContract` | 입력 수정 후 |
| 3 | `SCREEN_TYPE_DECISION` | `ThymeleafBindingContract` | `ScreenTypeDecision` | 동일 입력 멱등 |
| 4 | `COMPONENT_INVENTORY` | 화면 유형 + Registry Snapshot | `SelectedComponentInventory` | 입력 수정 후 |
| 5 | `DESIGN_MD_RULES` | Inventory + `DESIGN.md` | `AppliedDesignRules` | 입력 수정 후 |
| 6 | `COMPANY_TOKEN_MAPPING` | Rules + Profile Snapshot | `ResolvedDesignTokens` | 입력 수정 후 |
| 7 | `HTML_SKELETON` | Binding + Component + Rules + Tokens | `ThymeleafSkeleton` | 동일 입력 멱등 |
| 8 | `MODEL_BINDING` | Skeleton + Binding Contract | `BoundThymeleafView` | 동일 입력 멱등 |
| 9 | `RESPONSIVE_TRANSFORMATION` | Bound View + Platform Policy | `ResponsiveThymeleafViewSet` | 동일 입력 멱등 |
| 10 | `BUILD_RENDER_PARITY_VALIDATION` | 생성 프로젝트 + View Set + fixture | `ThymeleafGenerationReport` | 환경 복구 후 동일 입력 |

## 3. 단계 상태와 전이

상태는 `PENDING`, `RUNNING`, `SUCCEEDED`, `REVIEW_REQUIRED`, `FAILED`, `SKIPPED`만 허용한다.

```text
PENDING → RUNNING → SUCCEEDED
                  → REVIEW_REQUIRED → RUNNING
                  → FAILED          → RUNNING
PENDING → SKIPPED
```

- `SUCCEEDED`와 `SKIPPED`는 해당 실행에서 종결 상태다.
- `FAILED` 또는 `REVIEW_REQUIRED`가 발생하면 후속 단계는 실행하지 않고 `SKIPPED`로 기록한다.
- `SKIPPED.blockedByStage`는 반드시 자신보다 앞선 단계여야 한다.
- 알 수 없는 상태와 허용되지 않은 전이는 거부한다.

## 4. Issue 판정과 중단

| 최고 심각도 | 단계 상태 | 후속 단계 |
|---|---|---|
| 없음 또는 `WARNING` | `SUCCEEDED` | 실행 |
| `ERROR` | `REVIEW_REQUIRED` | 중단·`SKIPPED` |
| `FATAL` | `FAILED` | 즉시 중단·`SKIPPED` |

`FAILED`에는 최소 하나의 `FATAL`, `REVIEW_REQUIRED`에는 `FATAL` 없이 최소 하나의 `ERROR`가
있어야 한다. `SUCCEEDED`에는 `ERROR`나 `FATAL`을 넣을 수 없다.

## 5. Hash 정책

단계 입력 Hash는 다음 값을 결합한 canonical SHA-256 소문자 64자리 hex다.

```text
contractVersion
+ stage
+ previousStage.outputHash (1단계는 ROOT)
+ canonicalJson(currentInput)
```

- JSON 객체의 필드 순서 차이는 같은 Hash를 만든다.
- 단계, 계약 버전, 현재 입력 또는 선행 output이 바뀌면 Hash가 달라진다.
- 성공 단계는 `inputHash`와 `outputHash`를 모두 기록한다.
- 산출물 `ArtifactRef.contentHash`는 해당 output Hash와 교차 검증할 수 있어야 한다.
- 승인 Hash에는 각 단계 Hash를 포함하여 Profile·Registry·Token·DESIGN.md drift가 Apply 전에
  검출되도록 한다.

## 6. 재시도 정책

자동 재시도는 하지 않는다. 재시도는 같은 generation에 대한 명시적 요청으로만 수행한다.

- `SAME_INPUT_IDEMPOTENT`: 이전과 같은 `inputHash`일 때만 재실행하며 결과 Hash가 같아야 한다.
- `AFTER_INPUT_CHANGE`: 원인 입력을 수정하여 `inputHash`가 바뀐 경우에만 재실행한다.
- `AFTER_ENVIRONMENT_RECOVERY`: 빌드 도구·브라우저 등 외부 환경을 복구한 뒤 같은
  `inputHash`로 10단계를 재검증한다.
- 동일 입력 요청이 이미 성공했다면 재실행하지 않고 기존 불변 산출물을 반환한다.
- 입력이 바뀐 요청은 기존 승인을 재사용하지 않고 새로운 Preview/승인을 요구한다.

## 7. 단계 실행 증적

각 단계는 다음 필드를 보존한다.

```text
stage, status, inputHash, outputHash, contractVersion,
startedAt, completedAt, artifactRefs[], issues[], blockedByStage, attempt
```

- 실행된 단계의 `attempt`는 1 이상이다.
- `PENDING`과 `SKIPPED`의 `attempt`는 0이며 실행 시간과 output Hash가 없다.
- 완료 시간은 시작 시간보다 빠를 수 없다.
- 실패 과정에서 생성된 임시 파일은 정식 Artifact로 승격하지 않는다.

## 8. 구현·검증 근거

- `ThymeleafGenerationStage`: 고정 순서와 입출력·재시도 정책
- `ThymeleafGenerationStageStatus`: 상태와 전이 규칙
- `ThymeleafGenerationStageExecution`: 단계 실행 증적 불변조건
- `ThymeleafGenerationPipelineContract`: Issue 판정, Hash, 중단, 재시도 정책
- `ThymeleafGenerationPipelineContractTest`: 10단계 순서, 상태 전이, canonical Hash,
  선행 output chain, 재시도, 실행 증적 검증

10단계 전체 실행과 Report 영속화는 `R6-061`, 결정성·중간 FATAL 이후 실제 서비스 미호출 검증은
`R6-T20`에서 이 계약을 소비해 완성한다.
