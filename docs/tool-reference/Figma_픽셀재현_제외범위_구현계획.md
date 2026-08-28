# 픽셀재현(claude 경로) 제외 범위 구현계획

> [`Figma_픽셀재현_claude경로_구현계획.md`](./Figma_픽셀재현_claude경로_구현계획.md) §8 "1차 구현
> 제외 범위"에 있던 4개 항목 — ① 픽셀 일치도 측정 도구, ② 아이콘/이미지 asset 다운로드·삽입,
> ③ 반응형/브레이크포인트 대응, ④ 정교한 반복 dedup — 에 대한 구현명세서 + 구현목록. 4개는
> 서로 의존하지 않는 **독립적인 트랙**이라 하나의 문서에 트랙별로 정리했다. 구현 승인 전까지는
> 이 문서에 따라 코드를 변경하지 않는다.

---

## 0. 공통 배경

`Figma_픽셀재현_claude경로_구현계획.md`(옵션 B, 커밋 `369cbe1`)까지 완료된 상태를 전제로 한다.
`FigmaDesignSpecMapper.buildGeometryTree()`가 노드 트리를 부모-자식 구조로 보존해
`UiDesignSpec.geometryTree()` → `ScreenSpecification.componentGeometry()`로 넘기고,
claude 경로 프롬프트에 JSON 블록 + KRDS 가드레일 문구로 반영되는 것까지는 이미 되어 있다.

---

## 트랙 A — 픽셀 일치도 측정 도구

### A.1 핵심 재검토 — "픽셀" 대신 "구조" 일치도가 현실적 v1이다

코드를 다시 확인한 결과, **이 프로젝트에는 실제 스크린샷(PNG 픽셀 데이터)을 캡처하는 기능이
없다.** `CaptureWebPageTool.captureWebPage()`/`captureWebPageMultiViewport()`는 Chromium으로
화면을 열어 **DOM 구조**(selectorHint, bounding box 등)를 캡처할 뿐이다(`WebCaptureOrchestrationService`
및 관련 서비스 전체에서 `screenshot`/`.png`/`BufferedImage` 등 픽셀 관련 코드 0건 확인). 진짜
픽셀 diff를 만들려면 스크린샷 캡처 기능부터 새로 만들어야 하는데, 이는 이번 4개 항목 중 가장
큰 신규 엔지니어링이다.

**대신 이미 있는 인프라로 "구조 일치도"는 바로 만들 수 있다**: `WebCaptureAnalysisService.analyze()`
(`captureWebPage()`로 만든 artifact를 분석하는 `analyzeCapturedDesign()` Tool의 배후 서비스)가
`RenderedDesignSpecMapper`로 **Figma 경로와 동일한 `UiDesignSpec` 타입**을 만들어낸다. 즉
"Figma 원본 분석 결과"와 "생성된 화면을 다시 캡처해서 분석한 결과"가 **같은 모양의 데이터**로
이미 나온다 — 이 둘을 비교하면 픽셀 없이도 "구조적으로 얼마나 비슷한가"를 계산할 수 있다.

### A.2 목표 아키텍처

```
1. analyzeFigmaReference(figmaUrl) → UiDesignSpec(원본)          [기존, 무변경]
2. buildFullCrudPrompt(llmProvider=claude, screenSpecificationId=...)
   → Claude가 화면 생성 완료                                     [기존, 무변경]
3. captureWebPage(생성된 화면 URL) → CaptureArtifactSummary       [기존, 무변경]
4. analyzeCapturedDesign(artifactId) → UiDesignSpec(재캡처)       [기존, 무변경]
5. compareDesignFidelity(원본 analysisId, 재캡처 analysisId)      [신규 Tool]
   └─ DesignFidelityReport 반환(구조 일치도 % + 항목별 diff)
```

### A.3 데이터 모델 설계

`DesignFidelityReport`(신규 record, `model/design/`):
```java
public record DesignFidelityReport(
        String originalAnalysisId, String renderedAnalysisId,
        double archetypeMatch,        // 1.0 또는 0.0 (완전 일치/불일치)
        double componentOverlapRatio, // 두 UiDesignSpec.components() 타입 집합의 Jaccard 유사도
        double fieldRoleOverlapRatio, // fieldHints().role() 집합의 Jaccard 유사도
        double actionOverlapRatio,    // actions().type() 집합의 Jaccard 유사도
        List<String> missingInRendered,  // 원본에 있는데 재캡처에 없는 것
        List<String> extraInRendered     // 재캡처에만 있는 것
) {}
```

### A.4 핵심 로직 설계

`DesignFidelityComparator`(신규 서비스): 두 `UiDesignSpec`을 받아 `Set<String>` 기반 Jaccard
유사도(`교집합 크기 / 합집합 크기`)를 4개 항목(archetype/components/fieldRoles/actions)에 대해
계산. 순수 집합 연산이라 신규 라이브러리 불필요.

`DesignFidelityTool`(신규 Tool, `@McpToolRisk(READ_ONLY)` 또는 동급): `compareDesignFidelity(
originalAnalysisId, renderedAnalysisId)` — 두 `analysisId`로 `DesignReferenceAnalysisService.get()`
/`WebCaptureAnalysisService`가 이미 저장해둔 `DesignAnalysisResult`를 조회해 비교.

### A.5 리스크

| 리스크 | 대응 |
|---|---|
| "구조 일치도"는 진짜 픽셀 일치와 다르다(색상·정확한 좌표는 반영 안 됨) | 문서·Tool 설명에 "구조적 일치도이며 시각적 일치를 보장하지 않는다"고 명시 |
| `componentGeometry`(좌표)는 이 비교에 포함 안 됨 | 1차 범위 제외 — 좌표 비교는 허용 오차 설계가 별도로 필요해 2차로 미룸 |
| `captureWebPage()`는 서버가 미리 실행 중이어야 함 | 기존 제약 그대로 승계, 신규 문제 아님 |

### A.6 1차 구현 제외

- 실제 픽셀 스크린샷 캡처 및 이미지 diff(SSIM 등) — 스크린샷 캡처 기능 자체가 없어 훨씬 큰 별도 작업
- `componentGeometry` 좌표 단위 비교

---

## 트랙 B — 아이콘/이미지 asset 다운로드·삽입

### B.1 재사용 가능한 기존 코드

`FigmaDesignOrchestrationService.downloadToTempFile(String imageUrl)`(L433-441)이 이미
"URL → 임시 파일 다운로드"를 구현해뒀다(`URLConnection`, 10초 connect/30초 read 타임아웃,
확장자는 URL 문자열로 판별). 이번 트랙은 이 패턴을 "임시 파일"이 아니라 "생성 프로젝트의
리소스 디렉터리"로 저장하도록 확장하는 것이다.

`FigmaApiClient.queryImages(fileKey, List<String> nodeIds)` → `FigmaImageUrls`(nodeId별 CDN URL
+ 만료시각)도 이미 있다(현재는 `generateFromImage()`의 vision 분석 경로에서만 씀).

### B.2 목표 아키텍처

```
FigmaDesignSpecMapper.buildGeometryTree() 순회 중                      [수정]
  └─ node.type()이 "VECTOR"/"IMAGE" 이거나 fills[].type=="IMAGE"인 노드를
     List<String> imageNodeIds로 수집(NodeGeometry에 새 필드는 추가하지 않고,
     별도 수집 리스트로 UiDesignSpec에 실어 나름 — 최소 침습)
        ↓
BuildBoardPromptUseCase/BuildCrudPromptUseCase 계열이 아니라
claude 경로 생성 완료 "후" 사람이 명시적으로 부르는 별도 Tool로 분리:

downloadFigmaAssets(analysisId, outputPath)                            [신규 Tool]
  └─ FigmaApiClient.queryImages(fileKey, imageNodeIds)
  └─ 각 URL → outputPath/resources/images/{nodeId}.{ext} 로 다운로드·저장
  └─ 저장된 상대경로 목록을 반환 → Claude가 <img src=...>에 직접 반영
```

**설계 판단**: 이미지 다운로드를 claude 경로 프롬프트 생성에 자동 포함시키지 않고 **별도
Tool로 분리**한다. 프롬프트 생성은 순수 텍스트 조립(부작용 없음)인데, 파일 다운로드는
`@McpToolRisk(EXTERNAL)`급 부작용이 있는 행위라 성격이 다르고, 실패해도 코드 생성 자체는
막지 않아야 하기 때문이다.

### B.3 보안 리스크 및 대응 (신규 파일 쓰기 + 외부 URL 다운로드라 가장 중요)

| 리스크 | 대응 |
|---|---|
| SSRF — `queryImages()` 응답 URL을 검증 없이 그대로 다운로드 | Figma API 응답이 출처이므로 호스트를 Figma CDN 도메인(`figma-alpha-api.s3...` 등 실제 응답 확인 후 화이트리스트)으로 제한 |
| Path Traversal — nodeId를 파일명에 그대로 씀 | `nodeId`는 Figma 형식(`\d+:\d+`)만 허용하는 정규식 검증 후 파일명 생성(`CodeSaverTool`의 기존 Path Traversal 차단 패턴 재사용) |
| 대용량/무한 다운로드 | `downloadToTempFile()`의 기존 read timeout(30초) 재사용 + 응답 Content-Length 상한 체크 신규 추가 |
| outputPath가 승인되지 않은 임의 경로 | 기존 `ApprovedProjectWritePort`/`ProjectWritePolicy` 재사용(직접 `Files.write` 금지) |

### B.4 1차 구현 제외

- 벡터(SVG) 노드의 SVG 코드 자체 추출(현재는 PNG/JPG 래스터 export만 다룸)
- 이미지 압축/리사이즈

---

## 트랙 C — 반응형/브레이크포인트 대응

### C.1 재검토 — claude 경로 한정이라 엔지니어링이 아니라 "프롬프트 설계" 문제다

auto 경로(CSS 자동주입)는 이미 기각됐으므로, 이 트랙은 **claude 경로 프롬프트에 반응형
지시를 추가하는 것**으로 범위가 좁혀진다. 새 빌드 시스템이나 CSS 프레임워크가 필요한 게
아니라, 지금 프롬프트에 넘기는 `componentGeometry`(절대 좌표)를 Claude가 그대로 하드코딩된
`position:absolute`로 옮기지 않도록 **가드레일 문구를 보강**하는 작업이다.

### C.2 설계 — 착수 전 확인 결과, 애초 가정이 틀렸다

`src/main/resources/templates/egov/_ds_bundle.css.tpl`(KRDS CSS 번들 원본)을 실제로 grep해본
결과, **`krds-grid`/`krds-flex`/`krds-col`/`krds-row` 같은 범용 반응형 유틸리티 클래스는 이
KRDS 배포판에 아예 없다**(0건). 대신 확인된 사실은 이렇다:

```css
@media(max-width:767px){.krds-table-wrap .tbl.data thead th{font-size:...}}
@media(max-width:767px){.krds-table-wrap{overflow-x:auto; width:calc(100vw - ...)}}
```

`.krds-table-wrap` 같은 **기존 컴포넌트 클래스 자체에 반응형 `@media` 규칙이 이미 내장**되어
있다. 즉 "Bootstrap처럼 그리드 유틸리티를 조합해서 반응형을 만드는" 방식이 아니라, "정해진
KRDS 클래스를 그대로 쓰기만 하면 반응형은 KRDS CSS가 알아서 처리하는" 방식이다. 그래서
가드레일 문구도 "그리드 유틸리티를 써라"가 아니라 **"좌표를 인라인/고정폭으로 옮기지 말고
기존 KRDS 클래스를 그대로 유지하라"**로 수정했다:

```java
sb.append("- 좌표를 인라인 style이나 고정 px width/height로 옮기면 반응형이 깨집니다. ")
  .append("표·검색패널 등 기존 KRDS 클래스에는 이미 반응형 @media 규칙이 내장되어 ")
  .append("있으니(예: krds-table-wrap의 767px 이하 모바일 대응) 클래스만 정확히 유지하세요.\n");
```

`CrudPromptBuilderService`/`MasterDetailService`/`BoardPromptGenerationService` 3곳의
기존 `componentGeometry` 가드레일 블록에 동일하게 추가한다(3곳 모두 같은 블록을 갖고 있음,
§Figma_픽셀재현_claude경로_구현계획.md §5.4 및 Board llmProvider 구현 시 재사용).

### C.3 리스크

| 리스크 | 대응 |
|---|---|
| 문구만으로는 Claude가 100% 따르지 않을 수 있음 | LLM 지시의 근본적 한계 — 완전 보장 불가, 문서화만 |
| ~~KRDS 그리드 유틸리티 클래스명을 모름~~ | 착수 전 확인 결과 해당 클래스 자체가 없음을 확인 — 위 설계로 대체, 리스크 해소 |

### C.4 1차 구현 제외

- 실제 여러 viewport에서 렌더링 검증(자동화 테스트로 반응형 동작을 확인하는 것)

---

## 트랙 D — 정교한 반복 dedup

### D.1 현재 상태

`FigmaDesignSpecMapper.collapseRepeatedSiblings()`가 "같은 부모 아래 `type`+`name`이 완전히
같은 형제가 3개 이상 연속"일 때만 축약한다(엄격한 완전 일치, 순서 인접 조건).

### D.2 개선안

| 한계 | 개선 |
|---|---|
| `name`이 "Row 1"/"Row 2"처럼 인덱스가 섞이면 완전 일치 실패 | 이름 끝의 숫자를 제거하고 비교(`name.replaceAll("\\s*\\d+$", "")`) |
| 비연속(중간에 다른 노드가 끼면) 반복을 못 잡음 | 그룹핑을 "연속 구간"이 아니라 "부모 내 전체에서 정규화된 이름별로 묶기"로 변경 |
| `width`/`height`가 달라도 이름만 같으면 묶임(오탐 가능) | `width`/`height`가 허용 오차(예: 5px) 이내인 것만 같은 그룹으로 인정 |

### D.3 리스크

| 리스크 | 대응 |
|---|---|
| 오탐(실제로 다른 의미의 노드를 반복으로 오인) 확률 증가 | 허용 오차를 보수적으로(5px 이내)로 좁혀서 시작, 실제 Figma 샘플로 튜닝 필요 |

### D.4 1차 구현 제외

- 유사도 기반(비-정확 일치) dedup — 텍스트 내용이나 색상까지 고려한 퍼지 매칭은 범위 밖

---

## 전체 신규/수정 파일 목록

| 트랙 | 파일 | 유형 |
|---|---|---|
| A | `model/design/DesignFidelityReport.java` | 신규 |
| A | `service/DesignFidelityComparator.java` | 신규 |
| A | `tools/DesignFidelityTool.java` | 신규 + `McpConfig` 등록 |
| B | `service/FigmaAssetDownloadService.java` | 신규 |
| B | `tools/FigmaAssetDownloadTool.java` | 신규 + `McpConfig` 등록 |
| B | `service/FigmaDesignSpecMapper.java` | 수정(이미지 노드 수집) |
| B | `model/design/UiDesignSpec.java` | 수정(이미지 노드 id 목록 필드 추가, compat 생성자) |
| C | `service/CrudPromptBuilderService.java`/`MasterDetailService.java` | 수정(가드레일 문구 추가) |
| D | `service/FigmaDesignSpecMapper.java` | 수정(`collapseRepeatedSiblings()` 개선) |
| 전체 | `src/test/resources/mcp/tool-definitions-baseline.json` | 재생성(신규 Tool 2개 추가로 인해) |

---

## 단계별 구현목록

### Phase 1 — 트랙 D (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 1 | `collapseRepeatedSiblings()` 이름 정규화(숫자 접미사 제거) 추가 | 완료 |
| 2 | 비연속 반복 그룹핑으로 전환 | 완료 |
| 3 | 크기 허용 오차(5px) 비교 추가 | 완료 |
| 4 | 테스트 추가(인덱스 포함 이름+크기 오차 케이스, 비연속 그룹핑 케이스) | 완료(2건, 기존 14건 회귀 없음 확인) |

### Phase 2 — 트랙 C (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 5 | 실제 KRDS 반응형 유틸리티 클래스명 확인(착수 전 필수) | 완료 — grid/flex 유틸리티 클래스 자체가 없고, `.krds-table-wrap` 등 컴포넌트 클래스에 `@media` 규칙이 내장돼 있음을 확인(§C.2) |
| 6 | `CrudPromptBuilderService`/`MasterDetailService`/`BoardPromptGenerationService` 가드레일 문구 보강 | 완료(3곳 모두) |
| 7 | 테스트 추가(프롬프트에 새 문구 포함 확인) | 완료(Crud/MasterDetail 각 1건 신규, Board는 기존 테스트가 이미 검증) |

### Phase 3 — 트랙 A (완료)
| 순서 | 작업 | 상태 |
|---|---|---|
| 8 | `DesignFidelityReport` 모델 신설 | 완료 |
| 9 | `DesignFidelityComparator` 구현(Jaccard 유사도 4종) | 완료 |
| 10 | `DesignFidelityTool` 신규 + `McpConfig` 등록 | 완료 |
| 11 | MCP baseline 재생성 | 완료(`McpToolDefinitionSnapshotTest`/`McpToolRiskAnnotationCoverageTest`의 하드코딩된 개수 상수 37→38, 102→103 갱신 포함) |
| 12 | 테스트 추가(완전 일치/부분 일치/완전 불일치 케이스) | 완료(`DesignFidelityComparatorTest` 3건 + `DesignFidelityToolTest` 1건) |

### Phase 4 — 트랙 B (가장 큼, 보안 검토 필요)
| 순서 | 작업 |
|---|---|
| 13 | Figma CDN 도메인 화이트리스트 확정(실제 API 응답으로 확인) |
| 14 | `UiDesignSpec`에 이미지 노드 id 목록 필드 추가(compat 생성자) |
| 15 | `FigmaDesignSpecMapper`에서 이미지/벡터 노드 수집 로직 추가 |
| 16 | `FigmaAssetDownloadService` 구현(다운로드 + Path Traversal 방어 + `ApprovedProjectWritePort` 연동) |
| 17 | `FigmaAssetDownloadTool` 신규 + `McpConfig` 등록 |
| 18 | MCP baseline 재생성 |
| 19 | 보안 테스트 추가(악의적 URL/nodeId 차단 확인) + 정상 케이스 테스트 |

---

## 검증 방법

1. `./gradlew build` — 각 Phase 완료 시점마다 전체 테스트 통과 확인
2. 트랙 A: 동일 화면을 원본/재캡처 비교 시 1.0(완전 일치), 의도적으로 다른 화면 비교 시 낮은 값 확인
3. 트랙 B: 정상 Figma 이미지 노드 다운로드 확인 + 악의적 URL(허용 도메인 외)/잘못된 nodeId 형식 차단 확인
4. 트랙 C: `llmProvider=claude` 프롬프트에 반응형 가드레일 문구가 포함되는지 확인
5. 트랙 D: 비연속·인덱스 포함 이름 패턴의 반복 그룹이 실제로 축약되는지 확인

---

## 관련 문서

- [Figma_픽셀재현_claude경로_구현계획.md](./Figma_픽셀재현_claude경로_구현계획.md) — 이 문서의 전제가 된 옵션 B 구현
- [Figma_픽셀재현_claude경로_확장검토.md](./Figma_픽셀재현_claude경로_확장검토.md) — 최초 검토 원문
