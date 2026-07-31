# Semantic Figma 운영·마이그레이션 Runbook

> 문서 버전: 1.3  
> 작성일: 2026-07-27  
> 적용 범위: R8 운영 안정화, Registry Rollback, Legacy Frame Migration, 장애 우회

---

## 1. 변경 권한과 안전 원칙

다음 작업은 Preview와 사람의 명시적 확인 없이 실행하지 않는다.

- Design System Library Publish
- Component Registry 반영 및 Rollback
- 기존 Figma Frame Migration
- Published Component Key 변경
- 기존 Backup·Archive 삭제

Spring API는 `X-API-Key` 인증을 사용한다. MCP 도구는 별도의
`FIGMA_MCP_SHARED_SECRET` 인증을 유지한다.

---

## 2. Design System 생성부터 Publish까지

1. `DesignSystemSpec`을 Author Plugin에 불러온다.
2. Token, Variable, Component 변경 Preview를 확인한다.
3. Breaking, Deprecate, 누락 Component를 검토한다.
4. 승인 이벤트를 `/api/design-systems/reviews`에 기록한다.
5. 사람이 Figma Team Library를 Publish한다.
6. Author Plugin에서 Published Component/Variable Key가 포함된 Registry JSON을 내보낸다.
7. `POST /api/design-systems/{profileId}/registries/preview`로 Registry Preview를 실행한다.
8. 영향과 오류가 없을 때만
   `POST /api/design-systems/{profileId}/registries/apply?confirmed=true`로 확정한다.

---

## 3. Component Key 동기화와 드리프트

Registry 반영 전 다음 항목을 확인한다.

- Profile/Registry의 `profileId`, `profileVersion`, `registryVersion`
- Figma Library `fileKey`
- 모든 필수 Component의 `publishStatus=CURRENT`
- 기존 버전 대비 Component/Variable Key 변경

Key 변경은 일반 동기화가 아니라 Migration 대상이다. 먼저 다음 API로 영향 화면을 조회한다.

```http
GET /api/figma/operations/design-system-impact/{profileId}?profileVersion=...&registryVersion=...
```

드리프트 발생 시 현재 Registry를 덮어쓰지 않는다. 새 Registry 버전으로 Preview하고,
영향 화면 Migration이 끝난 뒤 Profile 연결 버전을 변경한다.

---

## 4. 화면 생성·갱신·보고

DEC-10의 운영 기본은 파일 입력이고 REST 직접 조회는 선택 기능이다.

1. 승인된 `ScreenSpecification`으로 `.figma-export-bundle.json`을 내려받는다.
2. FigmaScreenSpec Plugin에서 Preview를 확인한다.
3. 일반 변경은 `MERGE`, 전면 교체는 `REPLACE`를 선택한다.
4. Plugin이 생성한 보고서 JSON을 서버에 업로드한다.

```http
POST /api/figma/operations/reports
GET /api/figma/operations/metrics
GET /api/figma/operations/screens/{screenId}/reports
```

동일 `reportId` 재전송은 멱등 처리한다. 같은 ID의 다른 내용은 거부한다.

반복 동기화 때문에 REST 직접 조회를 활성화할 때는 실제 HTTPS 서버 도메인, Figma Plugin
origin CORS, 단기 토큰 발급·만료, 파일 fallback을 먼저 검증한다. 개발용
`localhost`/`127.0.0.1` 허용값을 운영값으로 간주하지 않는다.

새 Spec에서 제거된 노드는 DEC-12에 따라 항상 `🗄 Removed — {screenId}`로 Archive한다.
Plugin에서 즉시 삭제하거나 노드별 확인을 받지 않는다. Archive 영구 삭제는 Backup과 보고서를
확인한 뒤 사람이 별도로 수행한다.

---

## 5. Registry 한 버전 Rollback

Rollback은 Registry 원문을 삭제하지 않고 Profile이 참조하는 Registry 버전만 되돌린다.

```http
POST /api/design-systems/{profileId}/rollback
  ?profileVersion=...
  &registryVersion=...
  &confirmed=true
```

Rollback 후 Registry Audit, 영향 화면 조회, 대표 LIST/FORM 화면 MERGE Preview,
생성 보고서의 Registry/Fallback/Conflict 지표를 순서대로 확인한다.
`confirmed=false` 또는 누락 상태에서는 Rollback이 거부된다.

---

## 6. Legacy Frame Migration

대상은 과거에 전체 Root Frame을 매번 새로 생성하던 `jsp-to-figma-plugin` 결과와,
Published Library가 아닌 로컬 Component Instance를 포함한 화면이다.

관련 파일:

- `jsp-to-figma-plugin/`
- `figma-screen-spec-plugin/src/core.ts`
- `figma-screen-spec-plugin/src/code.ts`
- `figma-screen-spec-plugin/src/ui.html`
- `website-figma-contract/figma-screen-spec-v1.schema.json`
- `website-figma-contract/component-registry-v1.schema.json`

절차:

1. 승인된 Bundle을 FigmaScreenSpec Plugin에 불러온다.
2. 기존 Root Frame 하나를 선택한다.
3. Migration Preview를 실행한다.
4. `MANUAL_REVIEW` 매핑을 사람이 해소한다.
5. Plugin이 Root Frame 전체를 숨김 Backup으로 복제한다.
6. 기존 Frame에 `logicalNodeId`/`logicalType`을 부여한다.
7. 매핑된 로컬 Instance를 Registry의 Published Instance로 교체한다.
8. Migration Report와 `backupNodeId`를 보관한다.

Preview가 완전하지 않으면 적용 버튼은 활성화되지 않는다. 실패 시 Backup Frame을
다시 표시하고 실패한 Frame 대신 사용한다. Backup은 검증 완료 전 삭제하지 않는다.

---

## 7. Plugin 배포 방식(DEC-08)

### 7.1 현재 상태

두 Plugin(`krds-design-system-author-plugin`, `figma-screen-spec-plugin`) 모두 지금은
**개발용 manifest import만** 지원한다. 각 사용자가 이 저장소를 받아 `npm install && npm run build`를
실행하고, Figma Desktop의 **Plugins → Development → Import plugin from manifest**로
`manifest.json`을 직접 선택해야 한다. 업데이트가 생기면 다시 pull·rebuild·재-import해야 하며
Figma가 자동으로 새 버전을 알려주지 않는다.

### 7.2 선택지

| 방식 | 요구 조건 | 장점 | 단점 |
|---|---|---|---|
| **A. 개발용 manifest import(현재)** | 없음(모든 플랜) | 추가 비용·승인 없이 바로 사용 가능 | 사람 수만큼 반복 설치·업데이트, 코드 접근 권한 필요, 업데이트 누락 감지 어려움 |
| **B. 사내 공유 배포(빌드 산출물 배포)** | 없음(모든 플랜) | 코드 저장소 접근 없이 `dist/`+`manifest.json`만 받으면 됨(사내 위키·공유 드라이브에 zip 배포) | 여전히 수동 재-import 필요, 버전 관리를 문서로 추적해야 함 |
| **C. Figma 조직 전용 Private Plugin** | Figma **Organization/Enterprise** 플랜 필요(현재 확인된 사용자 플랜은 Team/Pro — 07번 문서 §6) | 조직 구성원이 Figma 플러그인 검색에서 바로 설치, 관리자가 배포하면 자동으로 최신 버전 노출 | 플랜 업그레이드 비용 필요. 실제 Team 플랜에서도 가능한지는 Figma 현재 요금제 페이지에서 재확인 필요(플랜 조건은 자주 바뀜) |
| **D. Figma Community 공개 배포** | 없음 | 설치는 가장 쉬움 | **권장하지 않음** — 두 Plugin 모두 사내 KRDS Registry 구조·서버 주소·`app.figma.mcp-shared-secret` 같은 내부 전용 개념을 그대로 담고 있어 공개 배포에 부적합 |

### 7.3 권장

- **소수 인원(디자인 시스템 담당자 몇 명)이 쓰는 지금 단계**: A(현재 상태) 유지. 추가 비용·승인 없이 바로 진행 가능.
- **여러 팀/여러 디자이너가 상시 사용하는 단계로 커지면**: B로 전환 — 코드 저장소 접근 권한 없이도 설치할 수 있도록 `dist/` 산출물과 설치 안내를 사내 위키에 정리해 배포.
- **조직 차원에서 Figma Organization/Enterprise 플랜을 이미 쓰고 있거나 도입할 계획이면**: C가 최선 — 수동 재-import 자체가 없어져 운영 부담이 가장 적음. 다만 플랜 업그레이드는 이 문서가 대신 결정할 수 없는 예산·조달 사안이라, 조직이 Figma 요금제 페이지에서 Organization 플랜의 Private Plugin 조건을 직접 확인하고 결정해야 한다.
- D는 두 Plugin의 현재 설계상 채택하지 않는다.

### 7.4 Plugin 설치·권한 장애

- Plugin `manifest.json`과 빌드된 `dist/code.js`가 같은 버전인지 확인한다.
- Figma Library가 현재 Team/Project에서 활성화됐는지 확인한다.
- `importComponentSetByKeyAsync` 실패 시 Registry Key와 Publish 상태를 확인한다.
- `MANUAL_REVIEW`가 반복되면 기존 Frame 이름을 업무 의미가 드러나도록 정리한다.
- 권한 문제로 Published Component를 가져올 수 없으면 Migration을 중단하고 Backup을 유지한다.

---

## 8. 장애 우회

REST/MCP 연결 장애 시 파일 경로를 사용한다.

- 시각 Reference: 기존 `source.figpack`
- Semantic 화면: `.figma-export-bundle.json`
- 결과 보고: `figma-generation-report-*.json`
- Migration 결과: `figma-migration-report-*.json`

`.figpack`을 `FigmaScreenSpec`으로 해석하지 않는다. Reference는 `jsp-to-figma-plugin`,
Semantic Bundle과 Migration은 `figma-screen-spec-plugin`에서 처리한다.

---

## 9. 지원 종료 기준

구형 JSON/Plugin 지원은 다음 조건을 모두 충족한 뒤 종료한다.

1. 대상 화면이 Design System 영향 조회에서 모두 식별된다.
2. 모든 운영 화면에 안정적인 `logicalNodeId`가 있다.
3. 로컬 Component가 Published Instance로 교체됐다.
4. 최근 운영 보고서에서 Registry 누락과 Fallback이 0이다.
5. Backup 보존 기간과 복구 책임자가 합의됐다.
6. 최소 한 릴리스 동안 구형 Import 사용 기록이 없다.

---

## 10. 릴리스 검증 명령

```bash
./gradlew test bootJar

cd website-figma-contract
npm test

cd ../jsp-design-extractor
npm test

cd ../jsp-to-figma-plugin
npm run typecheck
npm run lint
npm run build

cd ../figma-screen-spec-plugin
npm test
npm run typecheck
npm run lint
npm run build

cd ../krds-design-system-author-plugin
npm run typecheck
npm run lint
npm run build
```

---

## 11. DEC-07 Redaction 감사 결과(2026-07-28)

`DEC-07`(API 및 산출물 보안 정책) 완전 승인의 마지막 조건이던 "로그·산출물 전체에 걸친
Component/Variable Key redaction 감사"를 수행한 결과다. **결론: 실제 노출은 없었다.**
아래는 점검 범위와 근거다.

### 11.1 점검 범위와 결과

| 점검 대상 | 방법 | 결과 |
|---|---|---|
| MCP Tool 응답(`FigmaExportTool`, `DesignSystemTool`) | `FigmaMcpFacadeService`가 호출하는 서비스 메서드와 반환 타입 전수 확인 | `auditRegistry()`→`RegistryAuditResult`, `preflightRegistry()`→`RegistryPreflightResult` 모두 `profileId`/버전 문자열/`boolean`/`Issue` 목록만 반환. `resolutions` 필드도 논리 타입→논리 타입만 담고 실제 Figma Key는 없음 |
| `generateFigmaScreenSpec`/`validateFigmaScreenSpec` 응답 | `FigmaExportResult`/`FigmaScreenSpec` 구조 확인 | `FigmaScreenSpec`은 논리 타입(`krds.button` 등)만 담고 `ComponentRegistry` 자체를 포함하지 않아 채널과 무관하게 노출 대상이 없음 |
| 로그(`log.info`/`debug`/`warn`/`error`) | `service/designsystem`, `service/figma`, 관련 `mapper` 전체에서 로그 호출 전수 검색 | 총 3건, 전부 "테이블 초기화 완료" 문자열뿐이고 Registry/Profile/Spec 객체를 로깅하는 코드는 없음 |
| 저장 산출물(`DesignArtifactService.saveFigmaExport`) | `figma-screen-spec.json`/`figma-generation-report.json`/`metadata.json` 저장 내용 확인 | `ComponentRegistry`를 저장하지 않음(FigmaScreenSpec·생성 결과·메타데이터만) |
| 메시지 문자열에 Key 직접 삽입 여부 | `componentSetKey()`/`variableKey()`/`fileKey()` getter가 문자열 접합에 쓰이는 패턴 전수 검색(정규식) | 0건 — 모든 `DesignSystemIssue.message`는 논리 타입/ID만 사용 |
| Plugin(TypeScript) `console.log` | 두 Plugin의 `code.ts`/`ui.html` 전수 검색 | 0건 — 토큰·API Key가 브라우저 devtools 콘솔에 찍히는 경로 없음 |

### 11.2 의도적으로 원문 Key를 포함하는 경로(문제 아님)

`ComponentRegistrySyncResult`(REST `POST /api/design-systems/{profileId}/registries/preview`
등)와 그 안의 `ComponentRegistryDiff.Change.previousKey/candidateKey`는 실제 Figma Key를
그대로 담는다. 이는 결함이 아니라 **사람이 Registry 반영 전 실제 Key 변경을 눈으로
확인해야 하는 REST 전용 검토 채널**이기 때문이다. 이 타입 계열을 참조하는 Tool은
`DesignSystemTool` 하나뿐이며, 그 세 메서드는 모두 위 표에서 확인한 redaction된
`RegistryAuditResult`/`RegistryPreflightResult`만 반환하고 `previewRegistry`/`applyRegistry`/
`retryRegistry`(원문 Key 포함 타입 반환)는 호출하지 않는다 — REST 검토 채널과 MCP 채널이
코드 수준에서 이미 분리돼 있다.

### 11.3 발견한 경미한 사항(차단 요소 아님)

`ComponentRegistrySyncResult.FailureReport.retryToken`은 이름이 "token"이지만 실제로는
`{profileId}:{candidateVersion}` 형태의 예측 가능한 값이라 비밀값이 아니다. 접근 통제는
이 값이 아니라 `/api/**`에 걸린 기존 인증(X-API-Key·단기 토큰)이 담당한다. 악용 가능한
결함은 아니지만 이름이 오해를 살 수 있어 Java 모델에 이 사실을 명시하는 Javadoc을
추가했다(`ComponentRegistrySyncResult.java`). 재시도 흐름 자체나 REST 파라미터 이름은
바꾸지 않았다.

### 11.4 결론

DEC-07의 나머지 승인 조건("로그·산출물 redaction 감사")을 충족했다. 12번 문서 §4의
`DEC-07`과 R6-T05를 `[x]`로 갱신한다.

---

## 12. 변경 이력

| 버전 | 일자 | 변경 내용 |
|---|---|---|
| 1.3 | 2026-07-28 | DEC-10(FILE 기본·REST 선택) 운영 절차와 REST 활성화 조건, DEC-12(ARCHIVE 단일 정책) 및 사람에 의한 영구 삭제 경계를 §4에 반영 |
| 1.2 | 2026-07-28 | §11 "DEC-07 Redaction 감사 결과" 신설: MCP Tool 응답·로그·저장 산출물·메시지 문자열·Plugin console.log 전수 점검 결과 실제 Key 노출 없음을 확인. REST 전용 Registry 검토 채널(원문 Key 포함)과 MCP 채널(redaction됨)이 코드 수준에서 이미 분리돼 있음을 근거로 명시. 경미한 발견(`FailureReport.retryToken`이 실제로는 비밀값이 아닌데 이름이 오해를 살 수 있음)에 대해 Javadoc 보강. 기존 §11 변경 이력은 §12로 번호만 이동 |
| 1.1 | 2026-07-28 | §7을 "Plugin 배포 방식(DEC-08)"으로 확장: 개발용 manifest import(현재)/사내 공유 배포/Figma 조직 전용 Private Plugin(Organization·Enterprise 플랜 필요)/Community 공개 배포 4가지를 비교하고 단계별 권장안 추가. 기존 설치·권한 장애 대응은 §7.4로 이동(내용 변경 없음). Plugin 배포 방식 자체는 예산·조달이 걸린 조직 결정이라 이 문서가 대신 확정하지 않음 |
| 1.0 | 2026-07-27 | R8 운영 안정화·Registry Rollback·Legacy Frame Migration·장애 우회 절차 최초 작성 |
