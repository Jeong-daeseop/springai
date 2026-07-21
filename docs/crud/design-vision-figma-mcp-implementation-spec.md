# Figma REST API 디자인 참조 구현 명세서 (v3)

> **작성일:** 2026-07-19  
> **상태:** Release A 완료. 코드·자동 테스트·실제 Professional 팀 Figma REST/MCP 품질 대조를 통과했으며, 개인 소유 PC의 로컬 단일 사용자·공개 디자인 사용 범위에 적용한다. Release B는 미착수.  
> **기준 문서:** `design-vision-figma-mcp-adoption-impact-analysis.md` 5차 개정판  
> **대체 문서:** 기존 `design-vision-figma-mcp-implementation-spec.md` v2를 전면 대체한다.

---

## 1. 목적과 핵심 원칙

Figma 파일 또는 프레임 URL에서 레이어 트리와 기하 정보를 읽어 기존 `UiDesignSpec`을 생성하고, 현재 디자인 분석 → 화면명세 → CRUD 생성 파이프라인에 연결한다.

핵심 원칙은 다음과 같다.

1. 개발 세션에 연결된 Figma MCP를 애플리케이션 인프라로 간주하지 않는다.
2. 1차 구현은 **Figma REST API 직접 호출 + 결정론적 JSON 매퍼**로 한다.
3. Figma JSON을 다시 이미지로 바꿔 Vision LLM에 보내지 않는다.
4. 기존 `UiDesignSpec` 이후의 화면명세·CRUD 생성 계약은 유지한다.
5. 배포 토폴로지를 먼저 확정하고, 다중 tenant 기능은 필요한 프로필에서만 구현한다.
6. 캐시 동일성, 접근 권한, 시맨틱 재사용 호환성을 서로 다른 계약으로 다룬다.
7. 기존 운영 데이터가 있는 환경에서는 `CREATE TABLE IF NOT EXISTS` 수정으로 마이그레이션을 대신하지 않는다.
8. 개인 소유 PC에서 공개 디자인만 사용하는 로컬 단일 사용자 환경은 조직 외부 SaaS 승인을 완료 조건으로 요구하지 않는다. 업무 장비·업무망·내부 데이터에 사용하는 경우에는 조직 정책 확인 전까지 Figma 기능을 비활성화한다.

### 1.1 범위

공통 범위:

- Figma URL 검증과 fileKey/nodeId 추출
- Figma REST API 클라이언트
- Figma 노드 JSON → `UiDesignSpec` 매퍼
- Figma 분석 Tool과 서비스 경로
- 캐시 키·동시성 정합성 개선
- 시맨틱 재사용 계약의 source-aware 일반화
- 설정, 테스트, 운영 문서

조건부 범위:

- 다중 신뢰 경계 배포의 MCP 사용자 인증·인가
- tenant/owner/visibility 기반 DB·캐시·RAG 격리
- 사용자별 Figma OAuth
- 기존 MySQL·Redis·Vector Store 데이터 마이그레이션

1차 범위에서 제외:

- Figma MCP를 Java MCP 클라이언트로 호출하는 방식
- Figma 결과와 Vision LLM을 결합하는 하이브리드 분석
- Figma API 장애 시 무조건적인 Vision 자동 폴백
- Figma Design 외 FigJam 등 추가 `file_type`

---

## 2. 선행 결정과 배포 프로필

### 2.1 가장 먼저 확정할 결정

구현 착수 전에 다음 순서로 결정한다.

1. 배포 신뢰 경계
2. Figma 인증 모델
3. 업무 환경에서 사용할 경우 외부 SaaS·망분리 정책 승인 여부
4. 좌표 해석 범위
5. 캐시 권한 보존 정책

### 2.2 배포 프로필

| 프로필 | 설명 | 필수 보안 경계 | tenant 확장 |
|---|---|---|---|
| **P1 로컬 단일 주체** | 한 사용자가 자신의 PC에서 실행 | loopback 전용 바인딩 | 불필요 |
| **P2 공유 단일 신뢰 경계** | 여러 사용자가 하나의 공유 권한을 명시적으로 수용 | TLS와 VPN·방화벽·프록시 인증 등 서버 접근 통제 | 선택 |
| **P3 다중 신뢰 경계** | 사용자별 Figma 권한과 결과를 분리해야 함 | MCP 사용자 인증·인가 + 사용자별 OAuth 또는 동등한 주체 식별 | 필수 |

권장 1차 출시 프로필은 **P1**이다. P3는 §11의 다중 tenant 확장을 완료하기 전까지 지원하지 않는다.

P1의 안전 조건:

- 호스트 직접 실행: `server.address: 127.0.0.1`
- 컨테이너 실행: 컨테이너 내부는 `0.0.0.0`, 호스트 게시 주소는 `127.0.0.1:8080:8080`
- `/mcp/**`가 LAN 또는 인터넷에 노출되지 않았음을 배포 검사로 확인

### 2.3 결정 게이트

| 게이트 | 결정 | 1차 권장값 |
|---|---|---|
| D0 | 배포 프로필 | P1 |
| D1 | Figma 연동 방식 | REST API 직접 호출 |
| D2 | 인증 | P1: 사용자 PAT, P3: 사용자별 OAuth |
| D3 | 분석 방식 | 결정론적 매퍼, Vision 미사용 |
| D4 | 좌표 기준 | `absoluteBoundingBox`; render bounds는 보조 정보 |
| D5 | 지원 URL | `https://www.figma.com/file/...`, `/design/...` |
| D6 | URL node-id와 별도 nodeId 충돌 | 정규화 후 불일치 시 거부 |
| D7 | 분석 계약 값 | provider=`figma`, model=`deterministic-mapper`, promptVersion=`figma-mapper-v2` |
| D8 | 기능 기본값 | `enabled=false` |

---

## 3. 목표 아키텍처

```text
Claude Desktop
  → DesignReferenceTool.analyzeFigmaReference
  → FigmaReferenceValidator
  → FigmaApiClient
      ├─ 현재 요청자의 Figma 접근 권한 확인
      └─ file version + 선택 node JSON 조회
  → FigmaCacheKeyFactory
  → DesignAnalysisRepository.findExact
      ├─ hit  → 접근 정책 확인 → 기존 결과 반환
      └─ miss → FigmaDesignSpecMapper
                → DesignAnalysisRepository.saveOrGet
                → 신규 저장 승자만 RAG 인제스트
  → DesignAnalysisResult
  → createScreenSpecification
  → ScreenSpecification
  → buildFullCrudPrompt / 기타 생성 경로
```

중요한 동작 계약:

- 최신 `fileVersion`과 접근 권한을 확인하려면 Figma API 호출이 필요하다.
- 캐시는 Figma 호출 자체를 완전히 제거하기보다 JSON 매핑·DB 저장의 중복을 제거한다.
- P3에서는 캐시 hit도 현재 사용자의 접근 권한 또는 승인된 데이터 보존 정책을 통과해야 한다.

---

## 4. MCP Tool과 입력 계약

### 4.1 신규 Tool

```java
@Tool(description = """
    Figma Design 파일 또는 프레임을 분석하여 UiDesignSpec을 생성합니다.
    figmaUrl은 https://www.figma.com/file/... 또는 /design/... 형식만 허용합니다.
    URL의 node-id와 별도 nodeId가 모두 있으면 두 값이 일치해야 합니다.
    반환된 analysisId를 createScreenSpecification의 designAnalysisId로 전달하세요.
    """)
public DesignAnalysisResult analyzeFigmaReference(
        String figmaUrl,
        @Nullable String nodeId,
        @Nullable String featureType)
```

Tool 인자에는 PAT/OAuth 토큰, tenantId, ownerId를 노출하지 않는다. 인증 정보와 요청 주체는 서버 설정 또는 보안 컨텍스트에서 얻는다.

### 4.2 입력 정규화

- `featureType`: `null`/공백은 `crud`, 그 외 값은 소문자 정규화
- URL `node-id=1-2`와 API node ID `1:2`는 동일한 canonical form으로 변환
- URL과 별도 인자에 node ID가 모두 있으면 canonical form으로 비교
- node ID가 없는 파일 URL은 지원 여부를 별도 결정한다. 1차 구현은 명시적 node ID를 요구한다.
- fileKey와 nodeId는 로그·오류에 원문으로 남기지 않는다.

### 4.3 URL 검증

`FigmaReferenceValidator`는 `java.net.URI`로 파싱한 뒤 다음을 검사한다.

1. scheme가 정확히 `https`
2. host가 정확히 `www.figma.com`
3. 첫 경로 세그먼트가 `file` 또는 `design`
4. fileKey가 허용 문자·길이 정책 충족
5. fileKey allowlist가 설정된 경우 포함 여부
6. URL과 별도 node ID의 일치 여부

사용자가 입력한 URL은 이후 HTTP 요청 대상으로 재사용하지 않는다. 검증기는 fileKey와 nodeId만 반환하고 API 클라이언트는 고정된 `https://api.figma.com/v1`만 호출한다.

---

## 5. Figma REST API 클라이언트

### 5.1 책임

`FigmaApiClient`는 다음만 책임진다.

- 인증 헤더 구성
- 지정 node 조회
- file version 수집
- HTTP 상태를 도메인 오류로 변환
- timeout, rate limit, 재시도, 응답 크기 제한
- 리다이렉트 차단

매핑과 캐시 판단은 담당하지 않는다.

### 5.2 HTTP 보안

- API base URL은 상수로 고정하고 설정 오버라이드를 허용하지 않는다.
- JDK `HttpClient.Redirect.NEVER` 등으로 리다이렉트를 추적하지 않는다.
- PAT는 `X-Figma-Token`, OAuth는 `Authorization: Bearer ...` 계약으로 분리한다.
- 토큰, 전체 URL, fileKey, nodeId를 일반 운영 로그에 기록하지 않는다.
- 감사 로그가 필요한 P3에서는 내부 분석 ID와 해시된 출처 식별자를 기록한다.

### 5.3 운영 통제

| 항목 | 기본 계약 |
|---|---|
| connect timeout | 5초 |
| response timeout | 30초 |
| max attempts | 3회 이하 |
| retry 대상 | 429, 일시적 5xx, 연결 실패 |
| retry 금지 | 400, 401, 403, 404 |
| backoff | 지수 백오프 + jitter |
| `Retry-After` | 상한 이내이면 존중, 상한 초과면 즉시 안전한 실패 |
| response bytes | 설정된 최대 바이트 초과 시 중단 |
| depth | 기본 4, 운영 상한 고정 |

오류 코드는 최소 다음으로 구분한다.

- `FIGMA_AUTH_FAILED`
- `FIGMA_ACCESS_DENIED`
- `FIGMA_REFERENCE_NOT_FOUND`
- `FIGMA_RATE_LIMITED`
- `FIGMA_RESPONSE_TOO_LARGE`
- `FIGMA_API_UNAVAILABLE`
- `FIGMA_RESPONSE_INVALID`
- `FIGMA_FRAME_REQUIRED`
- `FIGMA_UNSUPPORTED_NODE_TYPE`

`FIGMA_FRAME_REQUIRED`는 `SECTION`, `CANVAS`, `DOCUMENT`처럼 여러 화면을 포함할 수 있는
노드가 분석 루트로 지정된 경우 사용한다. Release A는 단일 화면 `FRAME`만 분석한다.
Spring AI MCP 전송 계층이 Tool 예외를 일반 오류로 감쌀 수 있으므로 서버 내부에서는 구조화된
코드와 안전한 메시지를 유지하고, 클라이언트 오류 상세 응답 계약은 프레임워크 호환성을 확인해 별도로 확장한다.

Figma API 장애 시 이미지가 이미 확보되지 않았다면 Vision 폴백하지 않고 명시적으로 실패한다.

---

## 6. 도메인 모델과 매핑 계약

### 6.1 출처 모델

기존 `sourcePath`/`pageRange`는 하위 호환을 위해 유지하되 출처 타입과 Figma 메타데이터를 추가한다.

```java
enum DesignSourceType { FILE, FIGMA }

record FigmaSource(
    String fileKey,
    String nodeId,
    String fileVersion
) {}
```

`DesignAnalysisResult` 추가 필드:

- `DesignSourceType sourceType`
- `@Nullable FigmaSource figmaSource`
- `String analysisContractVersion`
- `String uiSpecSchemaVersion`

기존 JSON 역직렬화 기본값:

- `sourceType == null` → `FILE`
- `figmaSource == null` 허용
- `analysisContractVersion == null` → 기존 `promptVersion`

P3에서만 추가:

- `tenantId`
- `ownerId`
- `visibility` (`PRIVATE`, `TEAM`, `SHARED`)

### 6.2 `FigmaDesignSpecMapper`

매퍼 입력은 검증된 Figma node tree이고 출력은 기존 `UiDesignSpec`이다.

| Figma 정보 | `UiDesignSpec` 대상 | 규칙 |
|---|---|---|
| FRAME/COMPONENT/INSTANCE 계층 | `components` | 이름·타입·자식 구조를 규칙 기반 분류 |
| TEXT name/characters | `fieldHints` | DB 컬럼을 추측하지 않고 시맨틱 역할만 생성 |
| absoluteBoundingBox | `layout` | 상대 위치와 간격을 이산 버킷으로 변환 |
| 버튼성 component/name | `actions` | 표준 액션 enum 후보로 정규화 |
| 색·타이포·간격 | `tokens` | 안정적으로 추출 가능한 값만 포함 |
| prototype/interaction | `interactions` | 지원 가능한 trigger/result만 포함 |
| 불명확한 레이어 | `uncertainties` | 원인을 구체적으로 기록 |

좌표 처리:

- `absoluteBoundingBox`는 Figma 문서 모델의 기하 좌표로 사용한다.
- 회전, clipping, invisible node, effect가 해석에 영향을 주면 `uncertainties`에 남긴다.
- `absoluteRenderBounds`는 존재할 경우 보조 검증에 사용할 수 있으나 1차 레이아웃 판단의 단독 기준으로 사용하지 않는다.
- 픽셀 단위 좌표 자체를 현재 `UiDesignSpec`에 새로 저장하지 않는다.

매퍼는 결정론적이어야 하며 같은 입력·같은 mapper version에서 같은 결과를 반환해야 한다.

---

## 7. 캐시와 저장 정합성

### 7.1 캐시 동일성

canonical 문자열을 SHA-256으로 해싱해 `SOURCE_HASH`에 저장한다.

```text
sourceType=FIGMA
fileKey=<canonical>
nodeId=<canonical>
fileVersion=<immutable version>
featureType=<normalized>
mapperVersion=figma-mapper-v2
depth=<effective depth>
geometry=<effective option>
```

tenant는 P3에서 `TENANT_ID` 컬럼으로 분리하고 `SOURCE_HASH`에 중복 포함하지 않는 것을 권장한다.

기존 FILE 분석 경로도 `featureType`을 캐시 입력에 포함해 현재 결함을 함께 수정한다.

### 7.2 분석 계약 컬럼

1차 Figma 값:

- `PROVIDER_ID = figma`
- `MODEL_ID = deterministic-mapper`
- `PROMPT_VERSION = figma-mapper-v2`

`PROMPT_VERSION`은 기존 컬럼 호환을 위해 사용하고 도메인에서는 `analysisContractVersion`으로 일반화한다.

### 7.3 동시 캐시 미스

현재 `ON DUPLICATE KEY UPDATE RESULT_JSON=VALUES(RESULT_JSON)`은 DB PK와 JSON 내부 `analysisId`를 불일치시킬 수 있으므로 사용하지 않는다.

저장 계약은 `saveOrGet(proposed)`으로 변경하고 삽입 여부까지 반환한다.

```java
record DesignAnalysisSaveOutcome(DesignAnalysisResult result, boolean insertedByCaller) {}
```

1. 동일 유니크 키가 없으면 proposed 결과 삽입
2. 충돌하면 기존 행을 수정하지 않음
3. 유니크 키로 확정 행 재조회
4. 반환된 행의 `analysisId`와 JSON 내부 ID가 같음을 검증
5. 확정 행 ID와 proposed ID의 일치 여부로 `insertedByCaller`를 계산
6. 호출자는 반드시 `SaveOutcome.result()`를 사용
7. RAG 인제스트는 삽입 승자에게만 수행한다. 기존 행의 과거 인제스트 실패를 복구하는 작업은 별도의 멱등 재인덱싱 경로로 처리한다.

동시성 테스트는 실제 MySQL에서 두 트랜잭션으로 수행한다.

### 7.4 캐시와 접근 권한

- P1: 단일 사용자 캐시로 사용
- P2: 공유 결과라는 운영 정책을 명시하고 fileKey allowlist를 필수 적용
- P3: tenant 파티셔닝 + 객체 인가 + 권한 재확인/TTL/보존 정책을 함께 적용

P3에서 사용자별 파티셔닝만으로 Figma 권한 폐기를 처리했다고 간주하지 않는다.

---

## 8. 저장소 마이그레이션

### 8.1 공통 원칙

운영 테이블이 이미 존재하므로 `@PostConstruct`의 `CREATE TABLE IF NOT EXISTS`를 수정하는 것만으로 스키마 변경을 완료하지 않는다.

P3를 지원할 때는 Flyway 또는 승인된 버전 관리 SQL을 도입하고 다음 순서를 지킨다.

1. nullable 컬럼 추가
2. legacy 데이터 백필
3. 기존 유니크 키 제거
4. 새 유니크 키 추가
5. NOT NULL 전환
6. 검증 쿼리 실행

스키마 변경 주체는 하나로 통일한다. Flyway를 채택하면 `DesignAnalysisRepository`와 `ScreenSpecRepository`의 `@PostConstruct CREATE TABLE IF NOT EXISTS`는 제거하거나 스키마 검증 전용으로 바꾸고, 신규 설치용 초기 테이블 생성도 baseline migration으로 옮긴다. 애플리케이션 코드의 DDL과 버전 관리 migration이 동시에 스키마를 소유하게 두지 않는다.

### 8.2 `AI_DESIGN_ANALYSIS` P3 마이그레이션

```sql
ALTER TABLE AI_DESIGN_ANALYSIS
    ADD COLUMN TENANT_ID VARCHAR(64) NULL;

UPDATE AI_DESIGN_ANALYSIS
   SET TENANT_ID = 'legacy-default'
 WHERE TENANT_ID IS NULL;

ALTER TABLE AI_DESIGN_ANALYSIS
    DROP INDEX UK_DESIGN_ANALYSIS_CACHE,
    MODIFY TENANT_ID VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY UK_DESIGN_ANALYSIS_CACHE
        (TENANT_ID, SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION);
```

실제 마이그레이션은 사전 중복 검사, 백업, 롤백 SQL을 포함해야 한다.

### 8.3 `AI_SCREEN_SPECIFICATION` P3 마이그레이션

객체 단위 인가를 DB 조건으로 적용한다면 `TENANT_ID`, `OWNER_ID`, `VISIBILITY` 조회 컬럼을 추가하고 기존 `SPEC_JSON`도 같은 값으로 백필한다. DB 컬럼과 JSON이 불일치하면 시작 검증에서 실패시킨다.

---

## 9. 시맨틱 재사용과 RAG

### 9.1 호환성 계약

`findReusableCandidates`에서 fileKey/nodeId/fileVersion은 호환성 조건으로 사용하지 않는다.

호환성 조건:

- source type
- `UiDesignSpec` schema version
- analysis/mapper version
- featureType
- expected archetype

접근 권한 판정은 호환성 판정보다 먼저 수행한다.

### 9.2 P1/P2

- 기존 RAG 저장소를 사용할 수 있다.
- Figma와 Vision 결과의 분석 계약 호환성 분기를 추가한다.
- P2는 모든 사용자가 결과 공유를 수용했다는 운영 정책이 있어야 한다.

### 9.3 P3

필수 변경:

- 인제스트 메타데이터: `tenantId`, `ownerId`, `visibility`
- similarity search 요청 단계에서 접근 범위 filter 적용
- 필터가 적용된 집합 안에서 topK 계산
- 결과 반환 전 객체 인가 재검증
- Redis 키: `chat:chunk-ids:{tenantId}:{docId}`
- 논리 docId, 삭제 조건, 필요 시 Vector Store document ID도 tenant 네임스페이스 적용

### 9.4 기존 Redis·Vector Store 데이터 전환

P3 전환 시 권장안:

1. MySQL 원본 행에 `legacy-default` tenant 백필
2. 기존 `design_analysis` 벡터와 청크 추적 키 백업 후 제거
3. DB 원본에서 tenant/visibility를 포함해 전량 재인제스트
4. tenant filter가 없는 검색 경로가 남아 있지 않은지 검증
5. 공유 승인된 데이터만 `SHARED`로 별도 승격

구·신 메타데이터를 무기한 혼용하지 않는다.

---

## 10. 설정

```yaml
server:
  address: ${SERVER_ADDRESS:127.0.0.1} # P1 기본. 중앙 배포 프로필에서는 별도 승인 설정
  port: 8080

app:
  design-vision:
    figma:
      enabled: ${DESIGN_VISION_FIGMA_ENABLED:false}
      access-token: ${FIGMA_ACCESS_TOKEN:}
      allowed-file-types: [file, design]
      allowed-file-keys: []
      depth-limit: 4
      connect-timeout-seconds: 5
      response-timeout-seconds: 30
      max-response-mb: 10
      max-attempts: 3
      retry-max-delay-seconds: 15
      mapper-version: figma-mapper-v2
```

설정 검증:

- `enabled=true`인데 인증 정보가 없으면 애플리케이션 시작 실패
- P2/P3 공유 토큰 모드에서 allowlist가 비어 있으면 시작 실패
- depth/timeout/응답 크기 상한을 넘는 값은 시작 실패
- 토큰 값은 configuration properties의 `toString()`이나 actuator에 노출하지 않음

---

## 11. P3 다중 신뢰 경계 확장

P3는 다음이 모두 준비되어야 활성화할 수 있다.

1. `/mcp/**`의 신뢰 가능한 사용자 인증·인가
2. 사용자와 tenant를 제공하는 공통 보안 컨텍스트
3. 사용자별 Figma OAuth 또는 조직 정책이 승인한 동등한 인증
4. DB tenant 마이그레이션과 legacy 백필
5. 분석·화면명세 객체 단위 인가
6. tenant별 캐시 유니크 키
7. RAG 검색 전 filter와 Redis 네임스페이스
8. 기존 벡터 데이터 재인덱싱
9. 감사 로그와 데이터 보존·삭제 정책

하나라도 빠지면 P3 프로필로 배포하지 않는다.

---

## 12. 구현 파일 목록

### 12.1 공통 변경

| 파일 | 변경 |
|---|---|
| `model/design/DesignAnalysisResult.java` | sourceType, figmaSource, analysisContractVersion 추가 |
| `model/design/FigmaSource.java` | 신규 record |
| `model/design/FigmaReference.java` | 검증된 fileKey/nodeId record |
| `service/FigmaReferenceValidator.java` | URL·node ID·allowlist 검증 |
| `service/FigmaApiClient.java` | Figma REST 호출과 운영 통제 |
| `service/FigmaDesignSpecMapper.java` | JSON → `UiDesignSpec` 결정론적 매핑 |
| `service/FigmaCacheKeyFactory.java` | canonical cache key 생성 |
| `service/DesignReferenceAnalysisService.java` | `analyzeFigma`, FILE featureType 캐시 수정, 재사용 계약 일반화 |
| `mapper/DesignAnalysisRepository.java` | `SaveOutcome saveOrGet`, 캐시 정합성 보장 |
| `tools/DesignReferenceTool.java` | `analyzeFigmaReference` 추가 |
| `config/DesignVisionProperties.java` | Figma 중첩 설정 |
| `src/main/resources/application.yaml` | 비활성 기본 설정, P1 loopback 기본값 |

같은 `DesignReferenceTool`에 메서드를 추가하므로 `McpConfig`의 tool object 등록은 변경하지 않는다.

### 12.2 P3 조건부 변경

| 파일/자산 | 변경 |
|---|---|
| `build.gradle` | 선택한 DB migration 도구 의존성 |
| `db/migration/*` 또는 승인 SQL | tenant 컬럼·유니크 키·백필·롤백 |
| `model/design/ScreenSpecification.java` | tenantId/ownerId/visibility |
| `mapper/ScreenSpecRepository.java` | tenant 조건 조회 |
| `service/RagService.java` | tenant metadata, 검색 전 filter, Redis 키 네임스페이스 |
| 보안 컨텍스트/인가 서비스 | MCP principal → tenant/owner 전달 |
| Redis·Vector Store migration 작업 | legacy 제거·재인덱싱 |

---

## 13. 테스트 명세

### 13.1 필수 단위 테스트

- URL scheme/host/file_type 검증
- `www.figma.com.evil.com` 등 host 우회 거부
- URL node-id와 별도 nodeId 정규화·불일치 거부
- fileKey allowlist
- mapper의 정상·불확실성 폴백
- 회전/clipping/invisible node 처리
- canonical cache key에 featureType/version/options 포함
- 오류·로그에 토큰/fileKey 미노출
- 기존 JSON 하위 호환 역직렬화

### 13.2 API 클라이언트 테스트

- 200, 400, 401, 403, 404, 429, 5xx 변환
- `Retry-After`, backoff, 최대 시도
- redirect 미추적
- timeout과 최대 응답 크기
- node 응답의 version 누락·잘못된 JSON

### 13.3 저장소 통합 테스트

- 실제 MySQL에서 동일 캐시 키 동시 요청
- 저장 행 PK와 JSON `analysisId` 일치
- `saveOrGet`의 승자 결과 반환
- FILE 분석의 featureType별 캐시 분리
- P3: tenant가 다른 동일 입력의 충돌 없음
- P3: 기존 유니크 키 제거와 새 유니크 키 적용 검증

### 13.4 보안·RAG 테스트(P3)

- 다른 tenant의 analysisId/screenSpecificationId 조회 차단
- 존재하지 않는 ID와 권한 없는 ID의 외부 응답 동일화
- tenant filter가 topK 계산 전에 적용됨
- 같은 docId를 가진 tenant 간 Redis 청크 삭제 격리
- legacy vector 재인덱싱 후 무tenant 문서가 남지 않음
- 권한 폐기 후 cache 정책(TTL/재확인/보존정책) 준수

### 13.5 승인 기준

- 모든 필수 테스트 통과
- POC Figma 파일에서 좌표·레이어 매핑 결과 수동 검토 통과
- 대형 파일에서 timeout·응답 크기·rate limit 정책 검증
- 토큰 또는 원본 fileKey가 로그에 남지 않음
- 기능 비활성 상태에서 기존 Vision·화면명세·CRUD 테스트 회귀 없음

---

## 14. 구현 순서와 출시 계획

### Release A — P1 공통 기능

1. D0~D8 승인
2. 기존 cache 동시성 결함과 FILE featureType 캐시 키 수정
3. URL 검증기와 API 클라이언트
4. 결정론적 매퍼
5. 서비스·Tool·설정 연결
6. 단위·통합·POC 테스트
7. `enabled=false`로 배포
8. 개인 로컬·공개 디자인 범위에서 활성화. 업무 환경에서는 외부 SaaS 정책 확인 후 활성화

### Release B — P3 확장

1. MCP 인증·인가 별도 설계 승인
2. DB migration과 legacy 백필
3. tenant cache·객체 인가
4. RAG/Redis tenant 격리
5. Vector Store 재인덱싱
6. OAuth와 감사 로그
7. 교차 tenant 보안 테스트 후 활성화

### 롤백

- 즉시 기능 차단: `DESIGN_VISION_FIGMA_ENABLED=false`
- 애플리케이션 롤백 전에 신규 JSON 필드의 하위 호환성 확인
- DB migration은 백업과 역마이그레이션 SQL을 함께 준비
- Vector Store 재인덱싱 실패 시 기존 인덱스를 복원하되 P3 검색은 비활성화

---

## 15. 문서 갱신

구현과 함께 다음을 갱신한다.

- `CLAUDE.md`의 `DesignReferenceTool` 사용법
- `local-vision-design-reference-integration-review.md`의 망분리 하드 게이트
- `design-vision-tool-test-priority-detail.md`
- `docs/tool-catalog.md`
- `docs/tool-reference/MCP_Tool_전체목록.md`
- [`figma-pat-operations-runbook.md`](figma-pat-operations-runbook.md)의 운영 비밀정보·토큰 회전 절차
- P3 선택 시 tenant 데이터 migration/runbook

---

## 16. 완료 정의

Release A 완료 조건:

- P1 배포에서 Figma URL로 `DesignAnalysisResult` 생성 가능
- 동일 node/version/featureType 재분석 결과가 안정적으로 재사용됨
- `UiDesignSpec` 이후 기존 화면명세·CRUD 생성 경로가 변경 없이 동작
- 캐시 동시성 정합성과 FILE featureType 캐시 결함이 해결됨
- Figma 기능을 설정 한 번으로 완전히 비활성화할 수 있음
- P1 보안·운영·POC 검증 완료. 업무 환경에서 사용할 경우에만 조직 외부 SaaS 정책 확인 완료

Release B 완료 조건:

- 인증된 사용자 주체가 모든 DB·캐시·RAG 조회에 전달됨
- tenant 간 데이터·캐시·벡터·Redis 키가 격리됨
- legacy 데이터 migration과 재인덱싱이 검증됨
- 권한 폐기와 데이터 보존 정책이 캐시 hit에도 적용됨

이 완료 정의를 충족하기 전에는 해당 배포 프로필을 운영 활성화하지 않는다.

---

## 17. 구현 진행 현황

> **점검일:** 2026-07-19

### 17.1 Release A

| 항목 | 상태 | 근거 |
|---|---|---|
| D0~D8 코드 결정 반영 | 완료 | P1 loopback, PAT, REST, 결정론 매퍼, 지원 URL과 비활성 기본값 적용 |
| FILE featureType 캐시 분리 | 완료 | FILE canonical hash에 featureType 포함 |
| 동시 캐시 저장 정합성 | 완료 | `DesignAnalysisSaveOutcome`, 기존 행 불변, DB/JSON ID 검증 |
| URL 검증기 | 완료 | scheme/host/file type/node ID/allowlist 검증 |
| Figma API 클라이언트 | 완료 | timeout, retry, Retry-After, 상태 변환, 크기 제한, redirect 차단 |
| 결정론적 매퍼 | 완료 | component/field/action/layout/token/interaction과 rotation/clipping/invisible/effect/render bounds 불확실성 처리 |
| source-aware 재사용 | 완료 | source type, featureType, UiDesignSpec schema, 분석 계약, archetype 검증 |
| 서비스·Tool·설정 연결 | 완료 | `analyzeFigmaReference`부터 기존 화면명세 경로까지 연결 |
| 자동 테스트 | 완료 | 단위 테스트, 실제 MySQL 동시성 테스트, 전체 회귀 테스트 통과 |
| 실행 JAR | 완료 | `bootJar` 패키징 통과 |
| 실제 Figma REST POC | 완료 | 실제 파일·node 조회, file version과 document 수신 확인 |
| 실제 MCP end-to-end POC | 완료 | `analyzeFigmaReference`로 `FIGMA`/`CRUD_LIST` 결과 생성, schema version과 file version 확인 |
| 동일 입력 캐시 재사용 | 완료 | 실제 Figma 입력을 두 번 호출해 동일 analysisId 반환 확인 |
| 좌표·레이어 수동 품질 검토 | 완료 | Professional 팀의 1200×2570 KRDS 카드형 목록 Frame을 실제 MCP로 분석해 `FIGMA`, `CRUD_LIST`, 검색·필터, 목록 구조, 페이지네이션, 검색·신청하기 액션을 화면과 대조하여 모두 PASS |
| 분석 루트 노드 타입 계약 | 완료 | 단일 `FRAME`만 허용. `SECTION/CANVAS/DOCUMENT`는 `FIGMA_FRAME_REQUIRED`, 기타 타입은 `FIGMA_UNSUPPORTED_NODE_TYPE`으로 거부하고 회귀 테스트 추가 |
| PAT 운영·회전 절차 | 완료 | P1 최초 설정, 정기 회전, 긴급 폐기, smoke test와 P2/P3 확장 제한을 별도 runbook으로 문서화 |
| 외부 SaaS·운영 승인 | Release A 필수 조건 제외 | 개인 소유 PC·로컬 단일 사용자·공개 디자인 범위. 업무 장비·업무망·내부 데이터 사용 시에만 별도 확인 |

따라서 Release A는 **완료** 상태다. 기술 구현·실제 POC·P1 PAT 운영 절차·단일 Frame 입력 계약과 정확한 CRUD Frame의 MCP 품질 대조를 모두 마쳤다. 개인 소유 PC의 로컬 단일 사용자·공개 디자인 사용에서는 조직 외부 SaaS 운영 승인을 Release A 완료 조건으로 요구하지 않는다. 업무 장비·업무망·내부 데이터로 범위를 확장할 때만 조직 정책을 별도로 확인한다.

#### 17.1.1 남은 프레임 품질 검증 실행

애플리케이션을 실행한 상태에서 프로젝트 루트에서 다음을 수행한다.

```bash
set -a
source .env
set +a
node scripts/verify-release-a-figma-frame.js
```

스크립트는 API 호출량을 아끼기 위해 별도 사전 조회 없이 `2499:38449` 프레임을 MCP로 한 번 분석한 뒤,
출처·archetype·검색 패널·목록 구조·페이지네이션·검색 액션·신청하기 액션을
`PASS`/`FAIL`로 판정한다. `overall=PASS`이면 Release A의 남은 기술 완료 조건을 충족한다.
제한 상태만 확인하려면 `node scripts/verify-release-a-figma-frame.js --rate-only`를 사용하지만,
이 확인도 Tier 1 호출량을 1회 소비하므로 반복 실행하지 않는다. `REVIEW_REQUIRED`이면 실패 항목에 해당하는
매퍼 규칙을 보완한 뒤 회귀 테스트를 추가한다. 토큰·fileKey·nodeId 원문은 출력하지 않는다.

### 17.2 Release B

Release B의 MCP 사용자 인증·인가, OAuth, tenant DB·캐시·RAG 격리, legacy migration, 재인덱싱과 감사 로그는 모두 미착수다. 현재 구현은 P1 로컬 단일 사용자 프로필로만 사용한다.
