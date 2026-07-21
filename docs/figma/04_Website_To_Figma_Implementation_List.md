# Website → Figma 단계별 구현목록

**문서명**: 04_Website_To_Figma_Implementation_List.md  
**버전**: 1.5
**작성일**: 2026-07-21  
**상태**: Release 1 자동 구현·검증 및 목록 화면 Figma Desktop E2E 완료 — R4 잔여 수동 검수 진행

**기준 문서**: `03_Website_To_Figma_Implementation_Specification.md`  
**관련 문서**: `05_Overall_Architecture_Diagram.md` (01~04 통합 아키텍처 개요도)

---

## 1. 목적

본 문서는 Website → Figma 기능을 실제 작업 단위로 분해하고 의존관계, 산출물, 검증 방법과 완료 조건을 정의한다.

### 1.1 구현 현황 요약 — 2026-07-21

| 구간 | 상태 | 현재 증거 | 남은 승인 조건 |
|---|---|---|---|
| R0 계약 | 진행 | 중립 Schema, AJV 정상·enum·source hash·참조·경로 fixture, checksum 기록 | ZIP bomb/entry 수 fixture와 실행 프로젝트별 checksum 자동 비교 |
| R1 springai | 코드 구현 완료(검증됨) | 2026-07-21 직접 코드 검증: 컴파일 성공, `./gradlew test` 79개 테스트 전량 통과(실패 0), `McpConfig`에 `CaptureWebPageTool`/`DesignArtifactTool` 등록 확인, `DesignSourceMetadata` sealed interface·`LegacyDesignSourceAdapter` 존재 확인 | R1-01~R1-10 세부 체크박스가 실제 구현 상태를 반영하도록 갱신 필요(아래 §1.2 참고) — 코드 자체의 추가 구현은 불필요 |
| R2 extractor | 진행 | Chromium 4종 fixture, Text/pseudo, token/component golden, image/SVG/background image, layout confidence, JSONL 로그, 결정론 hash E2E | 실제 JSP 품질 baseline과 민감 selector 시각 승인 |
| R3 Plugin | 진행 | Figma Desktop 개발 Plugin 로드, 4종 `.figpack` preview·승인 후 1440×1200 Frame 생성, 목록 29개·상세 21개·등록/수정 각 13개 노드, image/SVG/Auto Layout, 선택 Component 2개와 Paint/Text Style, 중복 import 차단 | 정밀 시각 대조, Effect Style·Variable 선택 기능 검토 |
| R4 통합 | 진행 | Java 통합 테스트, 실제 MCP transport 6-Tool E2E·cache 재사용·화면명세 연결, extractor 브라우저 보안 E2E, 4종 Figma Desktop import, redirect/resource/Service Worker/WebSocket/popup/download/mask/package/path 보안 매트릭스 | 정밀 시각 대조와 실패 중간단계 정리 검증 |
| R5 완료 | 미승인 | Java/Node/Plugin 정적 빌드와 자동 테스트 통과 | R4 잔여 항목과 운영 승인 |

### 1.2 2026-07-21 코드 직접 검증에서 발견된 사항

03/04번 문서의 체크박스·요약표를 그대로 신뢰하지 않고 실제 코드(컴파일, 테스트 실행, 소스 직독)로 재검증했다. 두 종류의 결과가 나왔다.

**(a) 체크박스 미동기화 — 코드는 있으나 [ ]로 남은 경우**: R1(springai)은 132개 항목 중 1개만 [x]지만 실제로는 관련 클래스·테스트가 모두 존재하고 `./gradlew test`가 79개 테스트 전량 통과한다. R2 일부도 동일 패턴이다. 이 항목들은 개별 acceptance evidence를 재확인하며 순차적으로 [x] 전환이 필요하다 — 이번 검증에서는 전체를 일괄 체크 처리하지 않았다(개별 검증 없이 대량 체크하면 §1.1 R1 행이 겪었던 것과 같은 과신 문제를 반복하게 된다).

**(b) 실제로 확인된 미구현/스펙 미달 항목 — 아래 각 절에 신규 체크리스트 항목으로 추가함**:

- `jsp-design-extractor`, `jsp-to-figma-plugin` 모두 `lint` npm script 자체가 없음 (R0-01에 추가)
- 전용 Figma Plugin 채택 ADR 문서가 저장소에 없음 (R0-01에 추가)
- `jsp-design-extractor`가 03번 §3.1이 요구하는 Page Readiness/DOM·CSS Collector/Layout Analyzer/Component Recognizer 컴포넌트 분리 없이 단일 `server.ts`(200줄)에 압축 구현됨 — 동작은 하나 구조가 스펙과 다름 (R2-01에 추가)
- §7.3 CSS whitelist 중 `inset`, `text-shadow`, `filter`, `object-fit`, `object-position`과 width/height의 `raw` 원본값 보존이 미구현 (R2-05에 추가)
- §5.5 노드 필드 중 `selectorHint`, `sourceOrder`, `rotation`, `name` 누락 (R2-05에 추가)
- §7.4 contentHash canonicalization 7단계 중 필드 제외·키 정렬만 구현되고 배열 정렬 구분·좌표 반올림·색상 RGBA 정규화·Unicode NFC 정규화가 없음 (R2-09에 추가)
- §5.10/§5.11의 `extractor.schema SHA-256`·analyzer version, manifest의 node/asset/component/warning 요약 필드 누락 (R2-09에 추가)
- §6.4의 `CAPTURE_*` 오류 코드 체계 대신 임의 문자열 코드를 사용하고 모든 오류를 HTTP 400 하나로만 응답 (R2-02에 추가)
- §6.2 health 응답이 스펙 스키마(`serviceVersion`, `schemaVersions` 배열)와 다름 (R2-01에 추가)

아래 체크박스는 구현 파일 존재 여부가 아니라 각 acceptance evidence까지 확보했을 때 완료 처리하는 원장이다.

체크 상태 표기는 다음과 같다.

- `[ ]`: 미착수
- `[-]`: 진행 중
- `[x]`: 완료
- `[!]`: 차단 또는 결정 필요

우선순위:

- **P0**: Release 완료에 필수
- **P1**: 품질과 운영 안정성에 필수
- **P2**: 후속 개선

---

## 2. Release 구조와 의존관계

```text
R0 계약과 기반 결정
 ├─→ R1 springai 기반 구현
 ├─→ R2 jsp-design-extractor
 └─→ R3 jsp-to-figma-plugin
          ↓
       R4 통합/E2E
          ↓
       R5 Release 1 완료
          ↓
       R6 인증 SSR
          ↓
       R7 SPA
          ↓
       R8 다중 viewport
          ↓
       R9 운영 사이트 보안
```

Release 1은 R0~R5를 포함한다. R6~R9는 각각 독립적인 승인 게이트를 가진 Release 2 작업이다.

---

## 3. R0 계약과 기반 결정

### R0-01 저장소와 버전 정책 확정 — P0

- [ ] `jsp-design-extractor` 별도 프로젝트 생성 위치 결정
- [ ] `jsp-to-figma-plugin` 별도 프로젝트 생성 위치 결정
- [ ] `website-figma-contract` 중립 계약 프로젝트 또는 임시 계약 소유자 결정
- [ ] Java, Node.js, TypeScript와 Playwright 지원 버전 결정
- [ ] 세 실행 프로젝트와 계약 프로젝트의 릴리스 버전 호환표 정의
- [ ] `rendered-design-document-v1` 변경 정책 정의
- [ ] 전용 Figma Plugin과 Figma MCP 비교 ADR 작성
- [ ] Figma MCP는 개발·검수 도구이고 제품 실행 엔진이 아니라는 경계 승인
- [ ] `DesignSourceMetadata` 선행 전환 ADR 작성
- [ ] 전용 Figma Plugin 채택 ADR 문서 실제 작성·저장 (2026-07-21 확인: 결정 근거는 03번 §4.3에 있으나 별도 ADR 산출물 부재)
- [ ] `jsp-design-extractor`, `jsp-to-figma-plugin`에 lint 도구(ESLint 등) 도입 (2026-07-21 확인: 두 프로젝트 모두 `package.json`에 `lint` script 자체가 없음)

산출물:

- 저장소 구조 문서
- 런타임 버전 파일
- 호환성 표
- Figma 출력 경로 및 source metadata ADR

완료 조건:

- 모든 실행 프로젝트가 계약 프로젝트의 동일 schema version을 참조한다.
- schema breaking change 시 새 version을 만든다는 규칙이 문서화된다.

### R0-02 JSON Schema 작성 — P0

- [ ] `website-figma-contract`에 document/package manifest Schema 생성
- [ ] `.figpack` package version과 MIME type 정의
- [ ] 최소 유효 fixture 작성
- [ ] 목록 전체 fixture 작성
- [ ] 상세 fixture 작성
- [ ] 등록·수정 Form fixture 작성
- [ ] 잘못된 enum fixture 작성
- [x] 참조 무결성 위반 fixture 작성
- [x] 경로 탈출 fixture 작성
- [ ] schema SHA-256 기록 방식 정의
- [ ] `captureId`, `documentKey`, `contentHash` 생성·canonicalization 계약 정의
- [ ] `contentHash`를 document 최상위 단일 필드로 schema에 확정
- [ ] `source` 객체에 중복 `contentHash`가 없음을 negative fixture로 검증
- [x] `.figpack` ZIP slip, ZIP bomb, entry 크기·수·hash 제한 fixture 작성

완료 조건:

- Java와 TypeScript validator가 모든 fixture에 같은 판정을 내린다.
- node 및 asset 참조 무결성은 JSON Schema 이후 도메인 validator가 검사한다.

### R0-03 보안 결정 기록 — P0

- [ ] Release 1 배포 프로필을 P1 로컬 단일 사용자로 확정
- [ ] extractor loopback bind 확정
- [ ] `springai` capture 활성 시 loopback bind 강제 조건 확정
- [ ] `X-Extractor-Key` 전달과 회전 절차 정의
- [ ] main/resource origin allowlist 규칙 확정
- [ ] 민감 query 이름과 selector 기본값 확정
- [ ] artifact 보존 및 수동 삭제 절차 확정
- [ ] Release 1 WEB_CAPTURE RAG 미적재 원칙 확정
- [ ] Release 1 인증 세션 제외 원칙 확정
- [ ] 운영 사이트 기능 비활성 기본값 확정

완료 조건:

- Release 1에서 `AUTHORIZED_PRODUCTION_WEBSITE`가 설정과 코드 양쪽에서 거부된다.
- Secret과 전체 URL을 로그에 남기지 않는 정책이 테스트 항목으로 연결된다.

### R0-04 고정 JSP fixture 준비 — P0

- [ ] 목록 화면 준비
- [ ] 상세 화면 준비
- [ ] 등록 화면 준비
- [ ] 수정 화면 준비
- [ ] 고정 DB 또는 mock 데이터 준비
- [ ] 외부 네트워크 없이 로딩되는 CSS·폰트·이미지 준비
- [ ] 각 화면의 기대 component 목록 작성
- [ ] 각 화면의 `quality-baseline.json`과 승인 tolerance 작성
- [ ] 민감 selector screenshot mask fixture 작성

완료 조건:

- 동일 환경에서 fixture 데이터와 DOM 구조가 반복 실행마다 변하지 않는다.

---

## 4. R1 `springai` 기반 구현

### R1-01 설정 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/config/WebCaptureProperties.java
src/main/resources/application.yaml
.env.example
src/test/java/com/krdevops/springai/config/WebCapturePropertiesTest.java
```

- [ ] `app.web-capture` 설정 바인딩 추가
- [ ] enabled 기본값을 `false`로 설정
- [ ] extractor base URL과 API key 검증
- [ ] document key 전용 HMAC Secret 검증
- [ ] extractor key와 document key Secret 동일값 거부
- [ ] timeout 및 응답 크기 상한 검증
- [ ] 압축 해제 크기와 ZIP entry 상한 검증
- [ ] artifact base path 검증
- [ ] retention 설정과 만료 정리 정책 검증
- [ ] enabled profile 검증
- [ ] allowed origin 정규화와 중복 제거
- [ ] 운영 profile 활성화 거부
- [ ] `WebCaptureDeploymentGuard`로 `springai`와 extractor loopback 조건 검사
- [ ] 환경변수 예시 추가

완료 조건:

- 잘못된 설정은 애플리케이션 기동 시 안전한 메시지로 실패한다.
- 비활성 상태에서는 extractor 연결을 시도하지 않는다.

### R1-02 Capture 모델 — P0

예상 패키지:

```text
src/main/java/com/krdevops/springai/model/capture/
```

- [ ] `CaptureProfile` 구현
- [ ] `CaptureStatus` 구현
- [ ] `CaptureWebPageRequest` 구현
- [ ] `ViewportSpec` 구현
- [ ] `ReadinessSpec` 구현
- [ ] `CaptureResponse` 구현
- [ ] `CaptureArtifactSummary` 구현
- [ ] `DesignArtifactMetadata` 구현
- [ ] `RenderedDesignPackageManifest` 구현
- [ ] `FigmaImportArtifact` 구현
- [ ] `RenderedDesignDocument` 구현
- [ ] `RenderedNode` 구현
- [ ] `RenderedAsset` 구현
- [ ] `ComponentCandidate` 구현
- [ ] `CaptureWarning` 구현
- [ ] null collection 정규화와 immutable copy 적용
- [ ] capture ID/artifact ID 동일성 및 document key echo 계약 적용

완료 조건:

- Jackson round-trip 테스트가 통과한다.
- 모델 기본값이 schema 기본값과 일치한다.

### R1-03 URL 검증기 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureUrlValidator.java
src/test/java/com/krdevops/springai/service/WebCaptureUrlValidatorTest.java
```

- [ ] URI parser 기반 검증
- [ ] http/https scheme만 허용
- [ ] userinfo와 fragment 거부
- [ ] scheme/host/effective port 기반 origin 비교
- [ ] localhost와 IP literal 정책 구현
- [ ] 민감 query 마스킹
- [ ] redirect 요청 전 정책 검증과 응답 final URL 일치 검증 구현
- [ ] navigation/resource 요청 전 검증 계약 구현
- [ ] 전체 URL 대신 origin hash를 로그에 사용
- [ ] 마스킹 전 URL 단순 hash 저장 금지 및 query allowlist 적용
- [ ] 허용·거부·우회 URL 테스트 작성

완료 조건:

- 문자열 prefix, 혼동 host, userinfo, 다른 port와 redirect 우회가 차단된다.

### R1-04 Extractor HTTP Client — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureClient.java
src/main/java/com/krdevops/springai/service/WebCaptureException.java
src/test/java/com/krdevops/springai/service/WebCaptureClientTest.java
```

- [ ] 고정 base URL 사용
- [ ] `X-Extractor-Key` 헤더 적용
- [ ] connect/response timeout 적용
- [ ] redirect 비활성화
- [ ] 응답 body 크기 제한
- [ ] `.figpack` binary streaming 응답 처리
- [ ] capture ID/document key header와 manifest 일치 검증
- [ ] extractor error code 변환
- [ ] schema version 호환성 확인
- [ ] health 조회 구현
- [ ] API key와 body가 로그에 노출되지 않는지 테스트

완료 조건:

- timeout, 401, 403, 413, 5xx와 잘못된 JSON이 도메인 오류로 변환된다.

### R1-05 Design Document Validator — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/RenderedDesignDocumentValidator.java
src/test/java/com/krdevops/springai/service/RenderedDesignDocumentValidatorTest.java
```

- [ ] JSON Schema validator 의존성 추가 및 lock 갱신
- [ ] schema version 검증
- [ ] node ID 유일성 검증
- [ ] parent/child 양방향 정합성 검증
- [ ] node tree 순환 검증
- [ ] asset reference 검증
- [ ] 숫자와 크기 검증
- [ ] artifact 상대경로 검증
- [ ] 최대 node, asset, JSON 크기 검증

예상 추가 파일:

```text
src/main/java/com/krdevops/springai/service/RenderedDesignPackageValidator.java
src/test/java/com/krdevops/springai/service/RenderedDesignPackageValidatorTest.java
```

- [ ] `.figpack` manifest와 MIME 검증
- [ ] ZIP slip, 중복 entry, symlink와 case 충돌 거부
- [ ] 압축·압축 해제 크기와 entry 수 제한
- [ ] manifest entry 목록, 길이와 SHA-256 검증
- [ ] package/document schema checksum 일치 검증

완료 조건:

- extractor가 잘못된 결과를 반환해도 artifact 저장 전에 거부한다.

### R1-06 Artifact 저장 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/DesignArtifactService.java
src/test/java/com/krdevops/springai/service/DesignArtifactServiceTest.java
```

- [ ] 서버 UUID artifact ID 생성
- [ ] 임시 디렉터리 기록
- [ ] metadata/document ID와 hash 검증
- [ ] source `.figpack`, `preview.png`, assets 안전한 추출
- [ ] 동일 파일시스템 atomic move
- [ ] 기존 artifact overwrite 거부
- [ ] symlink와 path traversal 방어
- [ ] artifact 조회와 요약 생성
- [ ] 검증된 `.figpack` export
- [ ] retention 만료 artifact 정리
- [ ] 부분 기록 실패 시 정리
- [x] 동시 저장 테스트

완료 조건:

- 실패한 저장은 완성된 artifact로 조회되지 않는다.
- 저장 루트 밖으로 읽거나 쓸 수 없다.

### R1-07 Web Capture → UiDesignSpec 매퍼 — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/policy/WebCaptureProjectionPolicy.java
src/main/java/com/krdevops/springai/model/capture/SafeDesignProjection.java
src/main/java/com/krdevops/springai/service/RenderedDesignSpecMapper.java
src/test/java/com/krdevops/springai/policy/WebCaptureProjectionPolicyTest.java
src/test/java/com/krdevops/springai/service/RenderedDesignSpecMapperTest.java
```

- [ ] raw document → safe projection 경계 구현
- [ ] mapper가 raw node text/value를 입력받지 않도록 시그니처 제한
- [ ] 구조·role·style·token·승인 label allowlist 적용
- [ ] 기존 `SensitiveFieldPolicy` 정확 이름·토큰 판정 재사용
- [ ] archetype 판정
- [ ] layout shell/content width/density 판정
- [ ] form column layout 판정
- [ ] action/search panel placement 판정
- [ ] component candidate 변환
- [ ] field hint와 role 변환
- [ ] action 정규화
- [ ] token 후보 변환
- [ ] table row/detail 실제 값 제외
- [ ] input value, 사용자 프로필·avatar 문자열과 asset URL 제외
- [ ] confidence 미달 uncertainty 처리
- [ ] 목록·상세·등록·수정 fixture golden test
- [ ] PII sentinel이 projection/UiDesignSpec/분석 결과에 없는지 검증
- [ ] 정상 업무 필드 과잉 차단 회귀 테스트

완료 조건:

- 결과가 현재 `ScreenSpecAssembler`와 `ScreenSpecValidator` 계약을 통과한다.

### R1-08 디자인 분석 경로 통합 — P0

예상 변경 파일:

```text
src/main/java/com/krdevops/springai/model/design/DesignSourceType.java
src/main/java/com/krdevops/springai/model/design/DesignAnalysisResult.java
src/main/java/com/krdevops/springai/model/design/DesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/FileDesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/FigmaDesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/WebCaptureDesignSourceMetadata.java
src/main/java/com/krdevops/springai/model/design/LegacyDesignSourceAdapter.java
src/main/java/com/krdevops/springai/service/DesignReferenceAnalysisService.java
src/main/java/com/krdevops/springai/mapper/DesignAnalysisRepository.java
```

예상 신규 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureCacheKeyFactory.java
src/main/java/com/krdevops/springai/service/WebCaptureAnalysisService.java
```

- [ ] `DesignSourceType.WEB_CAPTURE` 추가
- [ ] `DesignSourceMetadata` 공통 모델을 WEB_CAPTURE보다 먼저 병합
- [ ] 신규 service 로직을 `sourceMetadata.sourceType()` 기준으로 전환
- [ ] 기존 flat source 필드는 deprecated JSON 호환용으로만 유지
- [ ] WEB_CAPTURE 전용 nullable top-level 필드 추가 금지
- [ ] FILE/Figma/WEB_CAPTURE 정적 factory 추가
- [ ] legacy FILE/Figma JSON을 metadata로 변환
- [ ] FILE/Figma JSON 하위 호환 확인
- [ ] WEB_CAPTURE 분석 계약 추가
- [ ] cache key에 content hash, feature type, schema와 mapper version 포함
- [ ] `DesignAnalysisRepository.saveOrGet` 재사용
- [ ] 시맨틱 재사용 실행 계약 추가
- [ ] WEB_CAPTURE는 RAG ingest를 호출하지 않도록 분기
- [ ] WEB_CAPTURE 시맨틱 후보 미노출
- [ ] 기존 DB JSON 역직렬화 회귀 테스트
- [ ] 모든 canonical constructor 호출부를 factory로 전환하고 컴파일·회귀 점검
- [ ] subtype/type 불일치 명시적 거부 테스트
- [ ] deprecated flat 필드 제거·DB migration 후속 조건 기록

병합 순서:

1. 첫 PR에서 `DesignSourceMetadata`, FILE/Figma factory, legacy adapter와 기존 JSON 호환 전환만 완료한다.
2. 첫 PR의 전체 회귀 테스트가 통과한 뒤 두 번째 PR에서 `WEB_CAPTURE` subtype과 분석 경로를 추가한다.
3. 두 단계를 한 PR로 구현해야 한다면 커밋과 검증 게이트를 같은 순서로 분리하고, 공통 모델 전환 실패 상태에서는 WEB_CAPTURE 코드를 활성화하지 않는다.

완료 조건:

- WEB_CAPTURE `analysisId`를 기존 `createScreenSpecification`에 전달할 수 있다.
- 기존 FILE과 FIGMA 테스트가 모두 유지된다.

### R1-09 오케스트레이션과 MCP Tool — P0

예상 파일:

```text
src/main/java/com/krdevops/springai/service/WebCaptureOrchestrationService.java
src/main/java/com/krdevops/springai/tools/CaptureWebPageTool.java
src/main/java/com/krdevops/springai/tools/DesignArtifactTool.java
src/main/java/com/krdevops/springai/config/McpConfig.java
src/test/java/com/krdevops/springai/service/WebCaptureOrchestrationServiceTest.java
src/test/java/com/krdevops/springai/tools/CaptureWebPageToolTest.java
src/test/java/com/krdevops/springai/tools/DesignArtifactToolTest.java
```

- [ ] capture 전체 흐름 구현
- [ ] 실패 단계별 안전한 오류 매핑
- [ ] `captureWebPage` 추가
- [ ] `getDesignArtifact` 추가
- [ ] `prepareFigmaImport` 추가
- [ ] `analyzeCapturedDesign` 추가
- [ ] Tool은 서비스에만 위임하도록 유지
- [ ] Tool description을 한국어로 작성
- [ ] `McpConfig` 파라미터와 `toolObjects`에 두 Tool 등록
- [ ] 대용량 document를 Tool 응답에서 제외

완료 조건:

- MCP Tool 응답은 artifact ID, 상태, 요약, 경고와 안전한 경로만 포함한다.

### R1-10 상태 점검과 문서 — P1

- [ ] `ProjectHealthService`에 capture 상태 추가
- [ ] extractor health와 schema 호환성 표시
- [ ] Chromium 준비 상태 표시
- [ ] artifact 경로 쓰기 상태 표시
- [ ] Tool catalog 갱신
- [ ] Tool 전체목록 문서 갱신
- [ ] `.env.example`과 운영 절차 갱신
- [ ] Secret 회전과 장애 대응 절차 작성

완료 조건:

- 기능이 비활성, 준비, 오류 중 어떤 상태인지 Tool 호출 전에 확인할 수 있다.

---

## 5. R2 `jsp-design-extractor` 구현

### R2-01 프로젝트 골격 — P0

- [ ] TypeScript 프로젝트 생성
- [ ] Playwright와 Chromium 설정
- [ ] lint, typecheck, unit test 설정
- [ ] `/v1/health` 구현
- [ ] `/v1/captures` 구현
- [ ] loopback bind 강제
- [ ] `X-Extractor-Key` 인증 middleware 구현
- [ ] 요청 body와 응답 body 크기 제한
- [ ] 성공 응답 `.figpack` binary streaming 구현
- [ ] 오류 응답만 안전한 JSON으로 반환
- [x] 구조화 file logging과 민감정보 필터 적용
- [ ] `/v1/health` 응답을 스펙 §6.2 스키마(`serviceVersion`, `schemaVersions` 배열, `browser`)에 맞춤 (2026-07-21 확인: 현재는 `schemaVersion` 단수 + `chromiumReady`만 반환)
- [ ] Page Readiness/DOM·CSS Collector/Layout Analyzer/Component Recognizer를 03번 §3.1이 요구하는 구분된 모듈로 분리 (2026-07-21 확인: 현재 `server.ts` 단일 파일 200줄에 압축 구현됨 — 기능은 동작하나 구조가 스펙과 다름)

완료 조건:

- 외부 interface에서 포트에 접근할 수 없다.
- 인증되지 않은 capture 요청이 거부된다.

### R2-02 입력과 URL 정책 — P0

- [ ] request schema 검증
- [ ] `LOCAL_JSP`만 허용
- [ ] main origin allowlist 적용
- [ ] resource origin allowlist 적용
- [ ] 빈 resource allowlist는 main origin만 허용
- [ ] navigation·redirect·resource 요청 전 정책 검증
- [ ] hostname의 해석 IP와 연결 대상 정책 검증
- [ ] service worker와 WebSocket 기본 차단
- [ ] popup 차단
- [ ] download 차단
- [ ] iframe request 정책 적용
- [ ] request interception 테스트
- [ ] 03번 §6.4의 `CAPTURE_*` 오류 코드 체계와 HTTP status 매핑(400/401/403/422/502/504/413/503/500) 구현 (2026-07-21 확인: 현재는 임의 문자열 코드를 사용하고 모든 오류를 HTTP 400 하나로만 응답)

완료 조건:

- main URL이 허용되어도 차단된 내부·외부 resource 요청은 정책에 따라 거부된다.

### R2-03 Browser Context — P0

- [ ] 요청별 새 Context 생성
- [ ] desktop viewport 설정
- [ ] locale/timezone/color scheme/reduced motion 설정
- [ ] browser extension 비활성 상태 확인
- [ ] 성공·실패 시 Context 정리
- [ ] 전체 capture timeout 적용
- [ ] 동시 capture 상한 적용

완료 조건:

- 이전 요청의 쿠키, storage와 page state가 다음 요청에 전달되지 않는다.

### R2-04 Page Readiness — P0

- [ ] DOMContentLoaded 대기
- [ ] `readySelector` 대기
- [ ] `hiddenSelector` 제거 대기
- [ ] `document.fonts.ready` 대기
- [ ] 이미지 로딩 상태 수집
- [ ] 애니메이션, transition, caret 정지
- [ ] DOM mutation 안정화 관찰
- [ ] timeout과 warning 구분

완료 조건:

- fixture의 skeleton 또는 loading indicator가 제거된 뒤 분석한다.

### R2-05 DOM/CSS 수집 — P0

- [ ] DOM 깊이 우선 순회
- [ ] 제외 tag 처리
- [ ] visible node 판정
- [ ] 접근성 role/name 수집
- [ ] label/placeholder/alt 수집
- [ ] password 및 input value 미수집
- [ ] `display:none` 제외
- [ ] `visibility:hidden`/`opacity:0` geometry 유지와 `visible=false` 처리
- [ ] 직접 자식 Text Node를 별도 TEXT node로 변환
- [ ] 부모 `textContent` 중복 방지
- [ ] `white-space`와 `text-transform` 정규화
- [ ] computed CSS whitelist 수집
- [ ] Bounding Box와 scroll offset 수집
- [ ] pseudo element synthetic node 생성
- [ ] iframe/Canvas 제한 노드 생성
- [ ] 안정적인 node ID 생성
- [ ] §7.3 CSS whitelist 중 `inset`, `text-shadow`, `filter`, `object-fit`, `object-position` 수집 (2026-07-21 확인: 미구현)
- [ ] width/height 등 원본 CSS 값을 `raw` 보조 필드로 보존 (2026-07-21 확인: 계산된 Bounding Box만 저장하고 `auto`/`%`/`calc()` 원본 값은 보존하지 않음)
- [ ] §5.5 노드 필드 중 `selectorHint`, `sourceOrder`, `rotation`, `name` 추가 (2026-07-21 확인: 현재 미포함)

완료 조건:

- 같은 fixture에서 node ID와 정규화된 내용이 안정적으로 유지된다.

### R2-06 자산 수집 — P1

- [x] img와 background image 추출
- [x] inline SVG 추출
- [x] MIME과 크기 검증
- [x] content hash 생성
- [x] 동일 자산 deduplication
- [x] 허용 origin만 다운로드
- [x] 실패 자산 warning 생성
- [x] artifact 상대경로만 반환

완료 조건:

- 차단 또는 실패한 자산 때문에 전체 capture가 불필요하게 실패하지 않는다.

### R2-07 Layout Analyzer — P0

- [x] flex horizontal/vertical 인식
- [x] gap과 padding 정규화
- [x] 겹침과 transform 검사
- [x] grid 정보 보존
- [x] block/inline/absolute 보존
- [x] confidence와 evidence 생성
- [x] 임계값 미달 absolute fallback
- [x] 부모 기준 상대좌표 계산

완료 조건:

- 추론이 원본 좌표를 삭제하지 않는다.
- 잘못된 Auto Layout 확정보다 fallback을 우선한다.

### R2-08 Component Recognizer — P0

- [x] semantic tag와 ARIA role 규칙
- [x] Header/Footer/Navigation/Breadcrumb 규칙
- [x] Search Panel/Toolbar 규칙
- [x] Form/Field Group/control 규칙
- [x] Button과 action 의미 규칙
- [x] Table/Pagination 규칙
- [x] confidence와 evidence 생성
- [x] 사용자 표시 text에만 의존하지 않는 보조 규칙

완료 조건:

- 네 가지 fixture의 기대 component 목록과 비교하는 golden test가 통과한다.

### R2-09 문서 생성과 자체 검증 — P0

- [ ] `RenderedDesignDocument` 조립
- [ ] URL 마스킹
- [ ] 민감 query 제거 기반 URL fingerprint
- [ ] capture ID/document key echo
- [ ] token 후보 집계
- [ ] warning 정규화
- [ ] 결정론적 content hash 생성
- [ ] canonical JSON key, 배열, 숫자, 색상, Unicode 정규화
- [ ] JSON Schema 검증
- [ ] 참조 무결성 검증
- [ ] 최대 node/asset/document 크기 적용
- [ ] 민감 selector mask가 적용된 `preview.png` 생성
- [ ] manifest/document/preview/assets `.figpack` 생성
- [ ] package entry hash와 자체 검증
- [ ] §7.4 canonicalization 7단계 중 배열 정렬 구분·좌표 반올림·색상 RGBA 정규화·Unicode NFC 정규화 추가 (2026-07-21 확인: 현재는 실행 가변 필드 제외와 JSON key 정렬만 구현됨)
- [ ] §5.10 `extractor` 보조계약 필드 중 `schema SHA-256`, layout analyzer/component recognizer version 추가 (2026-07-21 확인: 현재 `name`/`version`/`browserVersion`만 존재)
- [ ] §5.11 manifest에 node/asset/component/warning 요약 필드 추가 (2026-07-21 확인: 현재 `packageVersion`/`captureId`/`documentKey`/`contentHash`/`entries`만 존재)

완료 조건:

- extractor는 스스로 검증하지 못한 문서를 성공 응답으로 반환하지 않는다.

---

## 6. R3 `jsp-to-figma-plugin` 구현

### R3-01 프로젝트와 Manifest — P0

- [x] Figma Design Plugin 프로젝트 생성
- [x] `manifest.json` 작성
- [x] `documentAccess: dynamic-page` 적용
- [x] Release 1 network access `none` 적용
- [x] TypeScript typings, lint, build 설정
- [x] UI와 main sandbox message 계약 정의

완료 조건:

- Figma Desktop에서 개발 Plugin으로 로드된다.

### R3-02 `.figpack` import와 preview — P0

- [x] `.figpack` 파일 선택 UI
- [x] 최대 파일 크기 검사
- [x] ZIP entry/manifest/hash/schema 검증
- [x] asset byte 로딩과 document 참조 검증
- [x] node/asset/component/warning 요약
- [x] 지원하지 않는 기능 표시
- [x] 생성/취소 버튼
- [x] 사용자 승인 전 캔버스 미변경 보장

완료 조건:

- 잘못된 JSON이 캔버스를 변경하지 않는다.

### R3-03 기본 노드 생성 — P0

- [x] 최상위 Frame 생성
- [x] 부모부터 자식 순으로 생성
- [x] container → Frame 매핑
- [x] text → TextNode 매핑
- [x] image fill 매핑
- [x] inline SVG 매핑
- [x] SVG script, 외부 reference, event handler 제거와 크기 제한
- [x] border, radius, fill, opacity, effect 적용
- [x] 부모 기준 좌표 적용

완료 조건:

- 목록 fixture의 계층과 기본 시각 속성이 Figma에 생성된다.

### R3-04 Text와 Font — P0

- [x] font 사전 수집
- [x] `loadFontAsync` 호출
- [x] 원본 font 적용
- [x] fallback font 정책
- [x] font size, weight, line height, letter spacing 적용
- [x] fallback 결과 보고

완료 조건:

- 폰트 로드 실패가 전체 import를 중단시키지 않는다.

### R3-05 Auto Layout과 fallback — P0

- [x] horizontal Auto Layout
- [x] vertical Auto Layout
- [x] padding/gap/alignment 적용
- [x] absolute child 처리
- [x] grid/unknown absolute fallback
- [x] overflow와 clipping 처리
- [ ] 부모 영역 이탈 warning

완료 조건:

- Auto Layout을 적용해도 원본의 핵심 구조와 크기가 손실되지 않는다.

### R3-06 Component와 Style — P1

- [x] 반복 component 후보 표시
- [x] 사용자 선택 local Component 생성
- [x] 동일 style 탐색 및 재사용
- [x] Paint/Text style 생성
- [ ] Effect style 생성
- [ ] 선택적 local Variable 생성
- [x] Library publish 기능 제외 확인

완료 조건:

- 사용자가 선택하지 않은 후보를 Component로 자동 승격하지 않는다.

### R3-07 중복과 결과 보고 — P1

- [x] plugin data 기록
- [x] 동일 documentKey/contentHash 탐지
- [x] captureId와 documentKey plugin data 기록
- [x] 동일 결과 재생성 차단
- [x] 변경된 document는 새 Frame 생성
- [x] 성공·부분 실패 요약
- [x] 임시 최상위 Frame에서만 생성
- [x] 실패 시 이번 실행의 임시 Frame 정리
- [ ] 부분 결과 유지는 사용자 명시적 선택으로 제한
- [x] 생성된 최상위 Frame 선택 및 viewport 이동

완료 조건:

- 기존 Frame을 사용자 승인 없이 수정하거나 삭제하지 않는다.

---

## 7. R4 통합 및 E2E

### R4-01 계약 통합 — P0

- [ ] 계약 프로젝트의 document/package schema checksum 비교
- [ ] `springai`, extractor, Plugin에 포함된 schema checksum을 계약 프로젝트와 비교
- [ ] extractor 전체 fixture를 Java validator로 재검증
- [x] Spring export `.figpack`을 Plugin에서 검증
- [ ] schema mismatch 오류 메시지 확인

### R4-02 MCP E2E — P0

- [x] extractor 실행
- [x] JSP fixture 서버 실행
- [x] `captureWebPage` 호출
- [x] `getDesignArtifact` 호출
- [x] `prepareFigmaImport` 호출
- [x] `analyzeCapturedDesign` 호출
- [x] 기존 `createScreenSpecification` 호출
- [x] 동일 입력 cache 재사용 검증
- [x] WEB_CAPTURE RAG 미적재 검증

### R4-03 Figma E2E — P0

- [x] 상세 `.figpack` import
- [x] 등록 `.figpack` import
- [x] 수정 `.figpack` import
- [x] 목록 `.figpack` import
- [x] Frame 크기와 계층 대조
- [ ] 주요 Bounding Box 대조
- [ ] 색상·글꼴·radius·shadow 대조
- [x] 이미지와 SVG 대조
- [x] Auto Layout/fallback 대조
- [x] 중복 import 차단 검증
- [ ] `preview.png`와 생성 Frame 대조
- [x] 실패 시 임시 Frame 정리 검증

### R4-04 보안 E2E — P0

- [x] 비허용 origin 거부
- [x] redirect 우회 거부
- [x] 외부 resource 차단
- [x] service worker/WebSocket 차단
- [x] userinfo와 민감 query 마스킹
- [x] popup/download 차단
- [x] password/input value 미수집
- [x] table/detail/user profile PII sentinel projection 차단
- [x] 기존 `SensitiveFieldPolicy` 민감 토큰과 정상 업무 필드 회귀
- [x] 민감 selector screenshot mask
- [x] ZIP slip/ZIP bomb/hash 위변조 package 거부
- [x] extractor key 오류 처리
- [x] path traversal artifact 거부
- [x] 로그에 Secret과 전체 URL이 없는지 확인

---

## 8. R5 Release 1 완료

### R5-01 품질 게이트 — P0

- [ ] 전용 Figma Plugin 채택 ADR 승인
- [ ] `DesignSourceMetadata` 선행 전환과 FILE/Figma legacy JSON 호환 검증
- [ ] 최상위 단일 `contentHash` 계약과 `source.contentHash` 거부 검증
- [x] 네 종류 JSP fixture 성공
- [x] content hash 결정론 검증
- [x] captureId/documentKey/contentHash 역할 검증
- [x] 주요 component golden test 통과
- [x] 낮은 confidence fallback 검증
- [ ] Figma 구조 대조 통과
- [ ] fixture별 `quality-baseline.json` 승인 기준 통과
- [ ] 누락 자산과 font fallback 보고 확인
- [ ] PII sentinel이 safe projection, UiDesignSpec, 분석 결과와 RAG 호출 인자에서 완전히 제외됨을 확인

### R5-02 빌드 게이트 — P0

- [x] `springai` 전체 테스트 통과
- [x] `springai` bootJar 통과
- [ ] extractor lint 통과
- [x] extractor typecheck 통과
- [x] extractor test 통과
- [ ] Plugin lint 통과
- [x] Plugin typecheck 통과
- [x] Plugin build 통과

### R5-03 운영 게이트 — P0

- [x] 기능 기본 비활성 확인
- [x] capture 활성 시 `springai` loopback bind 강제 확인
- [x] loopback bind 확인
- [x] API key 설정 절차 확인
- [x] artifact 보존·정리 절차 확인
- [x] retention 만료 정리 확인
- [x] health check 문서 확인
- [ ] 장애·timeout·브라우저 미설치 안내 확인
- [ ] 운영·SPA·다중 viewport 비지원 표시 확인
- [ ] Release 1 로그인·세션 비지원 표시 확인
- [ ] WEB_CAPTURE RAG 미적재 확인

---

## 9. R6 Release 2A — 인증 서버 렌더링

### 착수 게이트

- [ ] Release 1 완료
- [ ] 인증정보 저장소 결정
- [ ] 사용자 또는 owner 식별 방식 결정
- [ ] storage state 보존·폐기 정책 승인

Release 1에서 인증된 JSP 요구가 확인되면 SPA보다 이 Release를 먼저 수행한다.

### 구현

- [ ] `storageStateRef` 불투명 ID 모델
- [ ] Secret 저장소 adapter
- [ ] profile/owner 권한 검증
- [ ] 요청별 인증 Context
- [ ] 세션 만료 판정
- [ ] 인증 실패 안전한 오류
- [ ] 로그인 화면 오탐 방지
- [ ] 사용자별 artifact와 cache 분리 기반

완료 조건:

- 인증정보를 MCP, artifact, 로그에 노출하지 않고 인증된 서버 렌더링 화면을 캡처한다.

---

## 10. R7 Release 2B — SPA와 동적 상태

### 착수 게이트

- [ ] Release 2A 또는 공개 SPA용 인증 제외 결정
- [ ] 허용 interaction step 목록 승인
- [ ] arbitrary JavaScript 비지원 원칙 확인

### 구현

- [ ] SPA readiness 전략
- [ ] hydration/skeleton 판정
- [ ] URL pattern 대기
- [ ] 사전 등록 click/fill/select/scroll step
- [ ] `stateId`와 step hash
- [ ] client-side route 변경 감지
- [ ] 지속 polling과 network idle 분리
- [ ] DOM mutation 안정화 정책
- [ ] 동적 상태별 artifact 생성

완료 조건:

- 공개 또는 승인된 SPA fixture에서 지정 상태를 반복 재현할 수 있다.

---

## 11. R8 Release 2C — 다중 viewport

### 착수 게이트

- [ ] 단일 viewport SPA 안정화
- [ ] viewport 목록과 최대 개수 결정
- [ ] 반응형 component matching 기준 승인

### 구현

- [ ] viewport별 독립 Context
- [ ] `RenderedDesignBundle` schema
- [ ] viewport별 document 보존
- [ ] 공통 component matcher
- [ ] 숨김·이동·교체 패턴 분석
- [ ] breakpoint observation 생성
- [ ] Figma viewport별 Frame 생성
- [ ] Component Variant 후보 제안
- [ ] 부분 성공 상태 처리

완료 조건:

- Desktop/Tablet/Mobile 문서를 독립 보존하면서 공통 컴포넌트와 변형 관계를 설명한다.

---

## 12. R9 Release 2D — 승인된 운영 사이트

### 필수 선행 승인

- [ ] 보안 ADR 승인
- [ ] 대상 사이트 소유권·분석 권한 확인 절차 승인
- [ ] MCP 사용자 인증·인가 적용
- [ ] tenant 모델 승인
- [ ] 개인정보 마스킹 정책 승인
- [ ] 보존·삭제·감사 정책 승인
- [ ] 운영망 capture agent 토폴로지 승인

### 구현

- [ ] capture agent 등록과 상호 인증
- [ ] 사용자·tenant별 job 격리
- [ ] 사용자·tenant별 artifact 저장소
- [ ] cache와 RAG namespace 격리
- [ ] 감사 로그
- [ ] rate limit과 quota
- [ ] 비동기 job queue
- [ ] 취소, 만료와 정리
- [ ] 개인정보 탐지와 마스킹
- [ ] 운영 site/resource allowlist 관리
- [ ] 관리자 비상 차단 기능

완료 조건:

- 권한 없는 사용자가 URL, 작업, artifact, cache, 인증 상태 또는 분석 결과를 조회하거나 재사용할 수 없다.

---

## 13. 추적 매트릭스

| 명세 영역 | 구현 항목 | 핵심 테스트 |
|---|---|---|
| 공통 JSON 계약 | R0-02, R1-05, R2-09, R3-02 | R4-01 |
| URL·SSRF 방어 | R0-03, R1-03, R2-02 | R4-04 |
| extractor 통신 | R1-04, R2-01 | R4-02 |
| artifact 저장 | R1-06 | R4-02, R4-04 |
| 공통 source metadata | R0-01, R1-08 | FILE/Figma legacy + WEB_CAPTURE 호환 테스트 |
| UiDesignSpec 연결 | R1-07, R1-08 | R4-02 |
| PII 안전 projection | R1-07 | R4-04, R5-01 |
| MCP Tool | R1-09 | R4-02 |
| DOM/CSS/Layout | R2-05, R2-07 | R4-03 |
| Component 인식 | R2-08 | R5-01 |
| Figma 생성 | R3-03~R3-07 | R4-03 |
| Figma 출력 경로 결정 | R0-01, R3 전체 | ADR 검토 + R4-03 |
| 인증 | R6 | Release 2A E2E |
| SPA | R7 | Release 2B E2E |
| 다중 viewport | R8 | Release 2C E2E |
| 운영 사이트 | R9 | 보안·격리 E2E |

---

## 14. 착수 순서 권고

첫 번째 구현 묶음:

```text
R0-01 → R0-02 → R0-03 → R0-04
```

두 번째 구현 묶음은 병렬 진행할 수 있다.

```text
springai: R1-01 → R1-08의 공통 source metadata 선행 전환
          → R1-02 → R1-03/R1-04/R1-05
extractor: R2-01 → R2-02 → R2-03/R2-04/R2-05
plugin: R3-01 → R3-02
```

세 번째 구현 묶음:

```text
R2-06/R2-07/R2-08/R2-09
  → R1-06/R1-07/R1-08의 WEB_CAPTURE 통합/R1-09
  → R3-03/R3-04/R3-05
```

마지막 Release 1 묶음:

```text
R1-10 + R3-06/R3-07
  → R4 전체
  → R5 전체
```

R6 이후에는 앞 단계의 완료 조건을 통과하지 않은 기능을 함께 묶어 개발하지 않는다.

---

## 15. 변경 이력

| 버전 | 작성일 | 변경 내용 |
|---|---|---|
| 1.5 | 2026-07-21 | 03/04번 문서와 실제 코드(컴파일·테스트·소스 직독) 재검증. §1.1 R1 "완료" 표현 정정, §1.2 검증 결과 요약 추가, 확인된 스펙 미달 9건을 R0-01/R2-01/R2-02/R2-05/R2-09에 신규 체크리스트 항목으로 추가 |
| 1.4 | 2026-07-21 | 실제 Figma Desktop 4종 import·Frame/asset/Auto Layout/Component·Style 생성과 중복 차단 검증 결과 반영 |
| 1.3 | 2026-07-21 | `05_Overall_Architecture_Diagram.md` 링크 추가 |
| 1.2 | 2026-07-21 | `DesignSourceMetadata` 선행 작업, 최상위 content hash 계약, Figma Plugin 결정 ADR, PII safe projection·SensitiveFieldPolicy 강제 작업 추가 |
| 1.1 | 2026-07-20 | 구현명세서 v1.1의 package, 보안, 식별자, RAG, screenshot, 결정론 및 품질 게이트 작업 반영 |
| 1.0 | 2026-07-20 | Release 1~2D 구현 항목, 의존관계, 완료 조건과 추적 매트릭스 작성 |
