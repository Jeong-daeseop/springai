# Figma Desktop 수동 QA 런북

> 작성일: 2026-08-18
> 대상: [12_Semantic_Figma_Design_System_Implementation_List.md](./12_Semantic_Figma_Design_System_Implementation_List.md)의
> Figma Desktop 런타임이 있어야만 검증 가능한 항목 — **R5-T02, R5-T03, R5-T08, R7-014, R8-023, R8-T04**
> 실행 주체: **사람**(Figma Desktop 네이티브 앱은 코딩 에이전트가 자동 조작할 수 있는 대상이 아님 — 상세
> 이유는 대화 기록의 "Figma Desktop 앱 런타임 요건" 설명 참고)

---

## 0. 먼저 할 일 — 기존 증적 재확인

새로 실행하기 전에, 이미 남아 있는 증적으로 일부 항목이 충족됐는지부터 확인하세요. 12번 문서의 체크박스가
아직 갱신되지 않았을 수 있습니다.

| 문서/파일 | 확인할 것 |
|---|---|
| [R7_Figma_Desktop_E2E_Run_2026-08-17.md](./R7_Figma_Desktop_E2E_Run_2026-08-17.md) | QnA 7화면 전체가 Figma Desktop에서 Apply까지 완료됨(생성 인스턴스 15~36개, Fallback 0, Gate PASSED) — **R7-014(Published Component Instance 재생성)의 런타임 검증 증적** |
| [KRDS_QNA_7화면_운영검증보고서_2026-08-16.md](./KRDS_QNA_7화면_운영검증보고서_2026-08-16.md) | 같은 7화면을 재조회(MERGE)했을 때 신규 0건·Archive 0건, 전부 기존 노드 재사용 — **R5-T02(REUSE 판정)의 런타임 검증 증적일 가능성이 높음** |
| `docs/figma/evidence/figma-generation-report-qna-*-it-c579be69-v1.json` (7개) | 각 파일의 `changes[]` 배열에서 `changeType` 값 분포 확인. `mode`/`reusedInstanceCount`/`createdInstanceCount`/`archivedNodeCount`/`fallbackCount`/`qualityGates` 필드가 실제 판정 근거 |

**두 문서의 Report ID 체계가 다르고(`-v1-`/`v15`) 두 문서 간 선후 관계·같은 Figma 파일을 가리키는지가
명확하지 않습니다.** 아래 절차를 진행하기 전에, 두 문서가 정말 같은 캔버스/같은 화면을 가리키는지, 그리고
"재사용 36건·신규 0건"이 실제로 "같은 Spec을 두 번째 Apply했을 때"를 의미하는지 먼저 본인이 직접
확인해 주세요. 맞다면 **R5-T02와 R7-014는 아래 1~2단계를 새로 실행할 필요 없이 바로 12번 문서 체크박스만
갱신**하면 됩니다.

---

## 1. 사전 준비 (공통)

1. **Plugin 빌드 확인**
   ```bash
   cd figma-screen-spec-plugin
   npm run build   # dist/code.js 생성 확인
   ```
   이미 `dist/code.js`가 있다면 최신 소스와 일치하는지만 확인(수정 후 재실행 시 반드시 재빌드).

2. **Figma Desktop에 Plugin Import** (최초 1회만)
   - Figma Desktop → 메뉴 → Plugins → Development → Import plugin from manifest…
   - `figma-screen-spec-plugin/manifest.json` 선택
   - Plugins → Development 목록에 `eGovFrame FigmaScreenSpec Export`가 보이면 완료

3. **테스트용 Bundle 파일 확보**
   - QnA 7화면은 이미 `build/figma-runtime-qna/qna-*.json`에 준비되어 있어 재사용 가능
   - 신규 Bundle이 필요하면 §3(R5-T08)의 서버 호출 절차로 생성

4. **알려진 차단 이슈**: 2026-08-17 실행 기록에 "macOS 파일 선택창의 접근성 행 선택과 열기 버튼
   활성화가 동작하지 않았다"는 메모가 있습니다. 이건 **자동화 에이전트가 macOS 파일 다이얼로그를
   프로그래밍적으로 조작하려다 실패한 것**이라, 사람이 직접 마우스로 클릭하는 이 런북 절차에서는
   해당하지 않습니다. 그래도 막히면 파일을 Finder에서 Plugin 창으로 드래그 앤 드롭하는 방법도 시도하세요.

---

## 2. R5-T02 — REUSE 판정 검증

**검증 목표**: 이미 캔버스에 존재하는 화면에 **동일한** Bundle을 다시 Apply했을 때, 새로 노드를 만들지
않고 기존 노드를 재사용하는지 확인.

1. `qna-list.json`(또는 §0에서 아직 안 만들어진 임의의 화면)을 Plugin에 처음 로드 → Apply(mode=MERGE) →
   Generation Report에서 `createdInstanceCount > 0`, `reusedInstanceCount = 0` 확인(최초 생성).
2. **같은 파일을 다시** Plugin에 로드 → 같은 화면에 다시 Apply.
3. 새 Generation Report에서 확인:
   - `reusedInstanceCount`가 1단계의 `createdInstanceCount`와 (거의) 같은가
   - `createdInstanceCount = 0`, `archivedNodeCount = 0`
   - `changes[]` 배열의 모든 원소가 `"changeType": "REUSE"`인가(구조가 안 바뀐 노드는 전부 REUSE여야
     함 — `core.test.mjs`의 `reconciliation reuses, moves, adds and archives deterministically` 테스트가
     보장하는 순수 로직이 실제 캔버스에서도 같은 결과를 내는지가 이 항목의 핵심)
4. **PASS 기준**: 캔버스에 중복 노드가 생기지 않고, Report의 재사용 카운트가 1단계 생성 카운트와 일치.

---

## 3. R5-T03 — ADD 판정 검증 (신규 노드만 추가)

**검증 목표**: 기존 화면에 새 논리 노드 하나만 추가된 Bundle을 Apply했을 때, 그 노드만 ADD되고 나머지는
전부 REUSE로 판정되는지 확인.

1. §2에서 이미 Apply된 화면의 Bundle JSON(`build/figma-runtime-qna/qna-*.json`)을 텍스트 에디터로 열기.
2. `figmaScreenSpec.content.children` 배열에 새 `logicalNodeId`를 가진 노드를 하나 추가(예: 기존 버튼
   노드를 복사해 `logicalNodeId`만 `.../extra-button`처럼 바꾸고 `type`은 이미 Registry에 있는
   `krds.button` 유지 — Registry에 없는 임의 타입을 쓰면 REQUIRED_COMPONENT_MISSING으로 막힘).
3. 수정한 Bundle을 Plugin에 로드해 같은 화면에 Apply.
4. Generation Report 확인:
   - `createdInstanceCount = 1`(추가한 노드 1개만)
   - 나머지 기존 노드는 `reusedInstanceCount`에 포함
   - `changes[]`에서 새로 추가한 `logicalNodeId`만 `"changeType": "ADD"`, 그 외 전부 `"REUSE"`
5. **PASS 기준**: 캔버스에서 새 노드 1개만 실제로 생성되고, 기존 노드 위치/속성은 그대로.

---

## 4. R5-T08 — 7종 요청 교차 시나리오의 Preview diff·Reconciliation 일치

**검증 목표**: REFERENCE_STYLE / IMAGE_REFERENCE / COMPONENT_SPECIFIED / MODIFY_EXISTING /
MULTI_SCREEN_FLOW / PLATFORM_CONVERT / TEXT_DESCRIPTION 7종 요청 각각이 실제로 만든 Bundle을
Figma Desktop에 적용했을 때, 서버가 보고한 Preview 결과(ArtifactRef·issues)와 실제 캔버스 Reconciliation
결과가 일치하는지 확인. (서버 단의 Bundle 생성 자체는 `FigmaDesignOrchestrationServiceTest`로 이미
검증돼 있으므로, 이 단계는 "서버가 만든 결과물을 실제로 Apply했을 때 Preview와 실제가 같은가"만 본다.)

### 4.1 사전 조건 — 로컬 서버 기동
```bash
docker start egov-mysql
redis-server
ollama run qwen3:8b
./gradlew bootRun   # 또는 bootJar 후 java -jar
```

### 4.2 화면별 Bundle 생성 → 다운로드 → Apply (7회 반복)

각 요청 유형마다 아래 순서를 반복합니다(Claude Desktop에서 MCP Tool을 호출하거나, X-API-Key를 붙인
curl로 REST를 직접 호출해도 됩니다).

1. 해당 유형의 생성 Tool 호출(예: `createDesignFromReference`, `createDesignWithComponents`,
   `modifyExistingDesign`, `createMultiScreenFlow`, `convertPlatform` 등) → `operationId` 확보
2. `generateFigmaBundleForOperation(operationId)` 호출 → 상태가 `PREVIEW_READY`인지 확인(`REJECTED`면
   issues를 보고 원인 해소 — 예: `screenSpecificationId` 누락, DB 미지정 등, 이미 서버 테스트로 이 분기
   자체는 검증돼 있음)
3. 반환된 `artifacts[].relativePath`로 Bundle JSON 다운로드
   (`GET /api/figma/screens/{screenId}/download`, `X-API-Key` 헤더 필요)
4. Plugin에 해당 JSON 로드 → Apply
5. **서버가 보고한 것과 캔버스 결과를 대조**:
   - 서버 `artifacts[].contentHash`가 실제로 로드한 JSON의 내용과 일치하는가(다운로드 경로 신뢰성)
   - 서버 issues에 없던 오류가 캔버스 Apply 중 새로 발생하지 않는가
   - Reconciliation 결과(ADD/REUSE/MOVE/ARCHIVE)가 Bundle 내용(신규 화면이면 전부 ADD, 기존 화면
     수정이면 일부만 변경)과 논리적으로 맞는가

### 4.3 결과 기록

7종 각각의 결과를 아래 표 형식으로 이 문서 하단 §6에 채워 넣거나 별도 `docs/figma/evidence/`
파일로 남기세요.

| 요청 유형 | operationId | Bundle 상태 | Apply 결과 | 비고 |
|---|---|---|---|---|
| TEXT_DESCRIPTION | | | | |
| REFERENCE_STYLE | | | | |
| IMAGE_REFERENCE | | | | |
| COMPONENT_SPECIFIED | | | | |
| MODIFY_EXISTING | | | | |
| MULTI_SCREEN_FLOW | | | | |
| PLATFORM_CONVERT | | | | |

---

## 5. R8-023 — MERGE 충돌 및 USER_OVERRIDE 보존

**검증 목표**: 사람이 캔버스에서 직접 고친 값이 재적용(MERGE) 후에도 사라지지 않는지, 그리고 서버가
바꾸려는 값과 사람이 고친 값이 동시에 있을 때 실제로 CONFLICT/보존 실패 Issue가 잡히는지 확인.
(`isUserOverridden(previousManagedValue, currentValue)` 순수 로직은 `core.test.mjs`의
`R5-T04: isUserOverridden은...` 테스트로 이미 검증됨 — 여기서는 그 판정이 실제 캔버스 편집 이력과
맞물려 동작하는지만 본다.)

### 5.1 정상 보존 시나리오
1. `qna-list.json`을 Apply(1차, ADD).
2. 캔버스에서 생성된 노드 중 아무 텍스트(예: 버튼 라벨)를 **Figma Desktop에서 직접 수동으로 편집**.
3. **같은 Bundle을 그대로** 다시 Apply.
4. 확인: 수동으로 고친 텍스트가 서버 값으로 덮어써지지 않고 그대로 남아 있는가.
5. Generation Report의 `changes[]`에서 그 노드가 REUSE로 잡히되, 속성 값 자체는 건드리지 않았다는
   근거(issue 또는 별도 필드)가 있는지 확인.

### 5.2 충돌 시나리오
1. 5.1의 상태에서, **Bundle JSON 쪽에서도** 같은 텍스트 값을 다른 문자열로 바꿔 수정.
2. 수정한 Bundle을 Apply.
3. 확인: 사람이 편집한 값과 서버가 새로 지정하려는 값이 다를 때 —
   - Plugin이 사람 편집을 우선 보존하는가, 아니면 명시적으로 CONFLICT Issue를 보고하고 그 노드만
     건너뛰는가(구현 의도가 "조용히 덮어쓰지 않는다"임 — 어느 쪽이든 "조용한 덮어쓰기"만 아니면 됨)
   - Generation Report의 `issues[]`에 보존 실패/충돌 관련 코드가 남는가

### 5.3 결과 기록
- 5.1/5.2 각각 PASS/FAIL과 실제 관찰한 Issue 코드를 §6에 기록.

---

## 6. R8-T04 — Figma Desktop 실제 노드 Migration (수동 QA)

이 항목은 문서 자체가 "수동 QA 잔여"로 명시한 항목입니다. 자동화 대상이 아니라 아래를 **눈으로 확인만**
하면 됩니다.

1. `npm run typecheck && npm run lint && npm run build && npm test`가 모두 통과하는지 재확인(코드 변경이
   없다면 이미 통과 상태일 것).
2. §2~§5를 진행하는 동안 캔버스에서 다음을 시각적으로 확인:
   - Published Component Instance가 실제로 KRDS/eGovFrame Team Library에서 정상적으로 당겨져 오는가
     (Import 실패로 대체 노드/placeholder가 생기지 않는가)
   - 여러 화면을 순차로 Apply했을 때 레이아웃이 서로 겹치거나 깨지지 않는가
3. 문제가 없으면 §2~§5의 실행 자체가 R8-T04의 "수동 노드 Migration 확인" 요건을 충족합니다 — 별도
   시나리오가 필요하지 않습니다.

---

## 7. 완료 후 처리

1. 각 섹션의 결과를 이 파일에 직접 채우거나 `docs/figma/evidence/`에 새 파일로 남기세요
   (기존 파일명 컨벤션: `figma-generation-report-{screenId}-{operationId}-v{n}.json`).
2. 결과가 모두 PASS면 `docs/figma/12_Semantic_Figma_Design_System_Implementation_List.md`의
   R5-T02·R5-T03·R5-T08·R7-014·R8-023·R8-T04 체크박스와 변경 이력을 갱신해 주세요(제가 진행해도
   되면 결과만 알려주시면 문서 갱신은 제가 대신합니다).
3. 일부만 PASS했거나 FAIL이 나오면, 어떤 단계에서 어떤 결과가 나왔는지 알려주시면 원인이 순수 로직
   버그인지(→ 제가 `core.ts`/서버 코드 수정 가능) 아니면 Figma 계정/Library 설정 문제인지 같이
   판단하겠습니다.
