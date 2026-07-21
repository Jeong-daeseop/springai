# Figma API/MCP(옵션 E) 실제 채택 시 영향 분석

> **작성일:** 2026-07-19
> **성격:** 순수 영향 분석 문서. 구현 여부·착수 여부는 결정되지 않았다. 코드는 수정하지 않았다(CLAUDE.md 원칙에 따름).
> **선행 문서:** `design-vision-coordinate-extraction-technology-feasibility-review.md`의 "옵션 E. Figma API/MCP" 결론 문단을 실제 코드베이스(`DesignReferenceTool` 등 현재 구현)에 대응시켜, "채택한다면 무엇이 구체적으로 바뀌어야 하는가"를 분석한다.
> **개정 이력:**
> - 1차 개정(2026-07-19): 초판 검토 결과 ①다중 사용자 인증 모델 누락 ②"좌표 정확도 100%" 과대 확정 ③캐시 키의 featureType 누락 미포착 ④"하위 파이프라인 영향 없음" 범위 과다 일반화 ⑤MCP-in-MCP 기각 근거 부족 ⑥DB 스키마 변경 필요성 과장 — 6건 반영.
> - 2차 개정(2026-07-19): 1차 개정 재검토 결과 ①사용자별 OAuth의 전제인 `/mcp/**` 인증 부재 미반영 ②시맨틱 재사용 호환성에 fileKey를 넣은 것은 목적과 불일치(캐시 동일성과 혼동) ③기존 NOT NULL 3컬럼(PROVIDER_ID/MODEL_ID/PROMPT_VERSION)의 Figma 저장값 계약 미정의 ④PAT 환경변수 전용 지침이 OAuth 모델과 충돌 ⑤URL 검증을 정규식 화이트리스트로만 설명(파싱 경계 불명확) ⑥표현·날짜 정리 — 6건 반영.
> - 3차 개정(2026-07-19): 2차 개정 재검토 결과, 다중 사용자 운영을 전제로 확장하면서 드러난 **데이터 격리 문제**를 반영 — ①배포 토폴로지(로컬/중앙단일/중앙다중) 구분 없이 인증 영향도를 단일하게 평가 ②캐시 적중이 Figma 접근 권한 검사를 우회할 수 있는 문제 누락 ③RAG/시맨틱 재사용 후보에 tenant·visibility 격리 없음 ④`figmaUrl`과 별도 `nodeId` 파라미터 충돌 규칙 미정의 ⑤Figma API rate limit·타임아웃·재시도 등 운영 통제 누락 ⑥`VisionAnalysisClient` 영향도가 기존 구현 변경과 신규 개발을 뭉뚱그림 — 6건 반영.
> - 4차 개정(2026-07-19): 3차 개정 재검토 결과, 중앙 다중 사용자 배포 분석에서 RAG 외의 격차를 추가 반영 — ①`localhost:8080` 접속이 서버의 loopback 전용 바인딩을 보장하지 않음(현재 설정에 `server.address` 없음) ②직접 `analysisId`/`screenSpecificationId` 조회 경로 전반에 객체 단위(tenant/owner) 인가가 없음 ③캐시 우회 방지책이 "여러 통제 중 하나"로 뭉뚱그려져 있어 목적이 다른 통제를 혼동 ④RAG 접근 필터가 topK 검색 이후가 아니라 **이전에** 적용돼야 함(현재 `RagService.search()`는 필터 없이 topK부터 조회) ⑤Figma 장애 시 vision 자동 폴백이 "대체 이미지 입력이 이미 존재할 때만" 가능한 조건부라는 점 누락 — 5건 반영.
> - 5차 개정(2026-07-19): 4차 개정 재검토 결과, tenant 캐시 파티셔닝 설계와 기존 DB 구조·동시성 사이의 구체적 충돌을 반영 — ①tenant별 캐시 파티셔닝이 현재 `UNIQUE(SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)` 유니크 키로는 구현 불가 ②기존 `ON DUPLICATE KEY UPDATE RESULT_JSON=VALUES(RESULT_JSON)` 저장 로직에 동시 캐시 미스 시 DB PK와 JSON 내부 `analysisId`가 불일치하는 기존 동시성 결함 존재(Figma 도입과 무관하되 API 지연으로 발생 확률 증가) ③RAG tenant 격리가 Vector Store 메타데이터뿐 아니라 Redis 청크 추적 키(`CHUNK_IDS_PREFIX+docId`)에도 필요 ④컨테이너/loopback 설명이 부정확 ⑤"중앙 단일 사용자 서버"에 '한 팀'을 포함한 분류가 모호 ⑥"저장소 조회 자체에 보안 주체 조건 포함이 유일한 안전 방식"이라는 단정이 과함 — 6건 반영.

---

## 0. 분석 대상 원문

> E(Figma): D와 같은 수준의 100% 정확한 좌표를 얻으면서도, D와 달리 "아직 구현 전 목업" 단계에서 바로 쓸 수 있다. 게다가 레이어/컴포넌트 이름이 잘 관리된 파일이라면 시맨틱 분류(옵션 A/B/C가 각자 방식으로 씨름하던 문제)까지 공짜로 딸려온다. 인프라 부담도 가장 적다(이 세션에도 이미 Figma MCP가 연결돼 있을 정도).

이 문단의 4개 주장(①좌표 정확도 100%, ②미구현 단계 사용 가능, ③시맨틱 분류 공짜, ④인프라 부담 최소)을 실제 코드 구조에 비추어 하나씩 검증한다.

---

## 1. 가장 중요한 선결 정정 — "세션에 연결된 Figma MCP" ≠ "앱에 연결된 Figma"

원문서의 ④번 근거("이 세션에도 이미 Figma MCP가 연결돼 있다")는 **이 대화(Claude Code 개발 세션)**에서 코딩 어시스턴트가 쓸 수 있는 `mcp__claude_ai_Figma__*` 도구를 가리킨다. 이는 다음과 완전히 별개다:

- `springai` 애플리케이션은 그 자체로 **독립된 MCP 서버**다(`spring-ai-starter-mcp-server-webmvc`, port 8080, Streamable HTTP). Claude Desktop이 이 서버에 접속해 `DesignReferenceTool.analyzeDesignReference` 같은 `@Tool`을 호출하는 구조다.
- 실사용 시나리오는 "**어떤 사용자든 Claude Desktop에서 `springai` MCP 서버를 통해** 화면을 생성한다"이지, "지금 이 개발 대화에서 나(코딩 어시스턴트)에게 Figma MCP가 연결돼 있다"가 아니다.
- 즉, **`springai` 서버 자신은 Figma에 대한 연결이 전혀 없다.** 이 세션의 Figma MCP 연결은 조사·POC를 위해 내가(코딩 어시스턴트가) 지금 활용할 수 있다는 뜻일 뿐, 배포된 애플리케이션이 신규 인프라 없이 Figma를 호출할 수 있다는 근거가 되지 못한다.

**결론: 원문서의 "인프라 부담이 가장 적다"는 주장은 부분적으로 재검토가 필요하다.** `springai`가 Figma 데이터를 읽으려면 둘 중 하나를 **새로 구축**해야 한다:

| 방식 | 내용 | 비고 |
|---|---|---|
| (1) Figma REST API 직접 호출 | `RestClient`/`WebClient`로 `api.figma.com`에 **선택한 인증 모델(§1-1)의 액세스 토큰**(PAT/OAuth 사용자 토큰/Plan Access Token)을 실어 HTTP 호출하는 신규 클라이언트 클래스 | 토큰 종류 자체는 클라이언트 구현에 큰 차이가 없으나, 토큰을 "어떻게 얻고 저장하는가"는 인증 모델에 따라 §1-1-1 수준으로 부담이 갈린다 |
| (2) Figma MCP를 Java 클라이언트로 임베드 | 애플리케이션 내부에 MCP 클라이언트를 추가해 Figma의 원격 MCP 서버(자체 OAuth 처리)를 호출 | **기각 확정이 아니라 선행 검증 필요**: Figma MCP 서버가 어떤 클라이언트 카탈로그를 공식 지원하는지, 현재 이 프로젝트의 Java/Spring AI MCP 클라이언트 스택이 그 지원 대상에 포함되는지부터 확인해야 한다. "스택 상 과함"은 잠정 판단이며 검증 전에 완전히 배제할 근거는 아니다. |

→ (1)이 현재로선 더 현실적인 경로로 보이나, §1-1(인증 모델)의 결정에 따라 (1)/(2) 모두 구현 범위가 크게 달라진다.

### 1-0. 배포 토폴로지 결정 — 인증 영향도를 좌우하는 선결 변수 (3차 검토 반영, 중요도: 높음)

§1의 "실사용 시나리오는 어떤 사용자든 Claude Desktop에서 `springai` MCP 서버를 통해 화면을 생성한다"는 문장은 **중앙의 단일 `springai` 서버를 여러 사용자가 공유 접속한다**는 배포 형태를 암묵적으로 전제하고 있었다. 그런데 실제로는 이 프로젝트의 `springai`가 다음 중 어느 형태로 배포되는지에 따라 인증 영향도 자체가 달라진다:

**분류 기준 정정(5차 검토 반영)**: 아래 표는 원래 "사람 수"로 배포 형태를 나눴으나, 이는 부정확하다 — 예를 들어 "한 팀"이 한 서버를 함께 쓴다면 사람 수는 적어도 이미 기술적으로는 다중 사용자이며 객체 단위 인가·감사가 필요할 수 있다. **사람 수가 아니라 "신뢰 경계(trust boundary)"를 기준으로 분류해야 한다**:

| 배포 형태(신뢰 경계 기준) | MCP 사용자 인증 필요 여부 | Figma 인증 방식 |
|---|---|---|
| **단일 보안 주체**(한 사람이 자기 PC에서 `springai` 실행, `localhost:8080`) — **단, loopback 전용 바인딩이 검증된 경우에 한함(아래 참고)** | 로컬 접근 제한 정도로 충분(외부 노출 없으면 `/mcp/**` 무인증도 상대적으로 위험 낮음) | 사용자 자신의 PAT를 본인 환경변수로 넣는 방식도 성립 |
| **여러 사용자지만 하나의 공유 권한을 명시적으로 수용한 단일 신뢰 경계**(한 팀이 "우리는 이 서버의 공유 토큰 권한을 함께 신뢰한다"고 명시적으로 합의하고 사용) | 서버 접근 인증 권장(필수는 아님) — 단, 팀 내 구성원 간에도 감사 로그·행위자 추적이 필요하다면 인증을 고려 | 단일 서버 토큰(PAT) 가능, 단 팀 전원이 그 토큰의 접근 범위 전체를 신뢰한다는 전제 |
| **사용자별 권한 분리가 필요한 다중 신뢰 경계**(서로 다른 Figma 접근 권한을 가진 사용자들이 한 서버에 접속, 서로의 결과를 봐서는 안 됨) | **사용자 인증·인가 필수** | 사용자별 OAuth 또는 엄격히 통제된 조직 토큰 |

- **§1-1/§1-1-1의 "MCP 서버 인증 체계 신규 도입이 필요하다"는 결론은 세 번째 행(중앙 다중 사용자 서버)에서는 정확하지만, 첫 번째·두 번째 행에서는 항상 필요한 것이 아니다.** 사용자별 로컬 배포라면 각자 자기 PC에서 자기 PAT로 돌리는 방식이 성립하고, 그 경우 `/mcp/**`의 무인증 상태가 지금 문서가 그리는 만큼 심각한 문제가 아닐 수 있다.
- **"로컬 배포라 안전하다"는 전제 자체가 검증되지 않았다(4차 검토 반영, 정정, 중요도: 높음)**: `Claude Desktop이 localhost:8080으로 접속한다`는 것은 **클라이언트가 접속하는 주소**일 뿐, 서버가 loopback(`127.0.0.1`)에만 바인딩된다는 뜻이 아니다. 실제로 `application.yaml`을 확인하면:
  ```yaml
  server:
    port: 8080
    tomcat:
      connection-timeout: PT1H
  ```
  **`server.address`가 지정되어 있지 않다** — 이 경우 Spring Boot/Tomcat은 기본적으로 **모든 인터페이스(`0.0.0.0`)에 바인딩**되므로, 같은 네트워크 대역의 다른 장비에서도 `사용자PC IP:8080`으로 `/mcp/**`에 접근할 수 있다. 즉 "사용자별 로컬 인스턴스"를 안전한 무인증 모델로 인정하려면, 실행 위치가 로컬이라는 것만으로는 부족하고 **다음이 명시적으로 확인·설정되어야 한다**:
  - **호스트에서 직접 실행하는 경우**: `application.yaml`에 `server.address: 127.0.0.1` 명시(IPv6 환경이면 `::1` 처리도 함께 확인).
  - **Docker/컨테이너로 실행하는 경우(5차 검토로 설명 정정)**: 컨테이너 안에서 정상적으로 서비스하려면 애플리케이션 자체는 보통 컨테이너 내부의 `0.0.0.0`에 바인딩해야 한다(컨테이너 내부에서 `127.0.0.1`에만 바인딩하면 일반적인 Docker 포트 포워딩으로는 그 서비스에 연결할 수 없다). 대신 **호스트 쪽 포트 게시(publish)를 loopback으로 제한**해야 한다 — 예: `-p 8080:8080`(모든 인터페이스에 공개, 위험) 대신 **`-p 127.0.0.1:8080:8080`**(호스트의 loopback에서만 접근 가능)으로 게시.
  - OS 방화벽이 8080 포트에 대한 인바운드를 실제로 차단하고 있는지 확인.
  - "여러 사용자지만 단일 신뢰 경계" 또는 "다중 신뢰 경계"로 서버를 노출하는 경우(중앙 서버)에는 위 loopback 논의와 무관하게 **명시적인 인증·방화벽·TLS**가 필요하다(§1-1).
  - 위 조건이 충족되지 않은 "로컬에서 실행 중"인 상태는 §1-0 표의 첫 번째 행이 아니라 사실상 **다중 신뢰 경계 서버와 동일한 노출 위험**을 가진 상태로 취급해야 한다.
- 이 프로젝트의 `CLAUDE.md`는 "서버를 기동한 뒤 Claude Desktop에서 MCP 서버 URL을 `http://localhost:8080`으로 등록"한다고 설명하고 있어, **현재 문서화된 기본 배포 형태는 로컬 실행에 가깝다.** 다만 위에서 확인했듯 이것이 곧바로 "안전한 loopback 전용 배포"를 의미하지는 않으며, 향후 조직 내 공용 서버로 전환할 계획이 있는지도 별도 확인이 필요하다.
- **따라서 §1-1 이하의 모든 분석은 "배포 형태가 무엇인지"와 "loopback 바인딩이 실제로 검증됐는지"에 대해 조건부로 읽어야 한다.** 이 문서는 최악의 경우(중앙 다중 사용자, 또는 loopback 바인딩이 검증되지 않은 로컬 실행)를 가정해 인증 영향도를 보수적으로 크게 잡았으나, 실제 승인 전에는 **배포 토폴로지와 네트워크 바인딩 설정을 함께 확정**해야 §1-1의 "인증 모델 결정"이 의미를 갖는다(§5 선행 필요 사항 최우선 항목으로 승격).

### 1-1. 다중 사용자 인증·권한 모델 — 누락돼 있던 핵심 전제 (중요도: 높음, 중앙 다중 사용자 배포를 전제로 한 분석)

위 표의 (1)은 "서버가 Personal Access Token(PAT) 하나를 환경변수로 들고 있다가 모든 요청에 쓴다"는 전제였다. 그런데 이 문서 §1이 스스로 명시한 실사용 시나리오는 "**어떤 사용자든** Claude Desktop에서 `springai` MCP 서버를 통해 화면을 생성한다"이다 — 이는 §1-0의 "중앙 다중 사용자 서버" 배포를 가정한 것이며, 그 가정 하에서는 다음과 같이 서로 맞지 않는다.

- **PAT 방식의 실제 의미**: 서버에 PAT 하나만 넣으면, **모든 MCP 사용자가 그 PAT 소유자의 Figma 권한으로** 파일을 조회하게 된다. 사용자별 접근 범위 구분이 없고, Figma 조직 내 다른 사용자의 비공개 파일도 PAT 소유자가 접근 권한이 있으면 누구나 조회 가능해진다.
- Figma 공식 인증 가이드(`https://developers.figma.com/docs/rest-api/authentication/`)는 PAT를 **개인 스크립트·로컬 도구용**으로 분류하고, 다중 사용자 애플리케이션에는 **OAuth 2**를 권장한다. 스코프도 최소 권한(`file_content:read` 등)으로 명시해야 한다.
- 따라서 인프라 방식(§1 표)보다 **먼저** 다음 운영 모델을 결정해야 한다:

  | 모델 | 설명 | 구현 부담 |
  |---|---|---|
  | 단일 서버 토큰 | 조직·허용 파일 목록을 한정하고 서버의 선택한 인증 모델(PAT 등)로 처리 | 낮음(§1의 (1) 그대로) — 단, "누구나 같은 권한" 리스크를 조직적으로 수용해야 함 |
  | 사용자별 OAuth | 사용자가 Figma 계정으로 로그인, 앱이 사용자별 액세스 토큰 발급·저장 | **매우 높음** — 단순 "세션-토큰 매핑"이 아니라 **MCP 서버 자체의 사용자 인증·인가 체계 신규 도입**이 선행돼야 함(아래 1-1-1 참고). 그 위에 OAuth 콜백 엔드포인트, 토큰 저장소·갱신(refresh)·폐기(revoke), 감사 로그가 추가로 필요 |
  | Organization/Enterprise Plan Access Token | 조직 관리자가 발급하는 조직 단위 토큰 | 중간 — PAT보다는 통제되나 여전히 "조직 단위" 공유 권한이며 개인별 감사는 제한적. **현재 Organization/Enterprise 대상 베타 기능**이라 대상 조직의 플랜/베타 참여 여부부터 확인 필요(Figma 인증 공식 문서: `https://developers.figma.com/docs/rest-api/authentication/`) |

#### 1-1-1. 사용자별 OAuth의 숨은 전제 — `/mcp/**`는 현재 인증이 전혀 없다 (2차 검토 반영, 중요도: 높음)

`SecurityConfig.java`를 확인한 결과, 현재 보안 필터 체인은 다음과 같다:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/mcp/**", "/sse/**", "/", "/ai/**",
        "/api/chat/**", "/api/ollama/**", "/api/documents/**").permitAll()
    ...
)
```

`/mcp/**`(Claude Desktop이 접속하는 경로)와 `/sse/**`는 **`permitAll()`로 완전히 인증이 없다.** `X-API-Key` 검증(`apiKeyFilter`)도 경로가 `/api/`로 시작할 때만 적용되므로 `/mcp/**` 요청에는 관여하지 않는다.

이는 사용자별 OAuth 모델에 다음과 같은 **선행 전제 결함**을 만든다 — "이 MCP 요청을 보낸 사람이 누구인지"를 신뢰할 수 있는 인증 정보가 애초에 없으므로:
- 어떤 사용자의 Figma OAuth 토큰을 골라 써야 할지 판단할 근거가 없다.
- OAuth 콜백이 돌아왔을 때 그 결과를 **어느 MCP 사용자에게 귀속**시켜야 할지 알 수 없다.
- 다른 사용자의 토큰이 잘못(또는 악의적으로) 사용되는 것을 막을 방법이 없다.
- 감사 로그에 "누가" 이 Figma 파일을 조회했는지 기록할 행위자 정보가 없다.

**결론**: "사용자별 OAuth"는 Figma 쪽 인증 서브시스템만 새로 만드는 문제가 아니라, **`springai` MCP 서버 자체에 사용자 인증·인가 경계를 새로 도입**해야 성립하는 훨씬 큰 전제 작업이다. 이는 이 문서가 다루는 "Figma 옵션 채택"의 범위를 넘어 MCP 서버 아키텍처 자체의 변경이므로, §5 선행 필요 사항 최우선 항목으로 별도 명시한다.

- **이 결정에 따라 영향 범위가 완전히 달라진다.** OAuth를 택하면 "신규 HTTP 클라이언트 하나 추가"가 아니라 **MCP 서버 인증 경계 신규 도입 + Figma 인증 서브시스템**(콜백, 토큰 라이프사이클, 사용자-토큰 바인딩, 감사 로그)이 되어, 이 문서 §3 매트릭스의 "인프라" 항목 평가 자체를 바꾼다.
- 본 문서 §2 이하의 컴포넌트별 분석은 편의상 "단일 서버 토큰" 모델을 **잠정 가정**하고 작성되었다는 점을 명시한다. 실제 채택 시 이 가정이 유지되는지부터 확정해야 한다(§5 선행 필요 사항 1번으로 승격).

---

## 2. 컴포넌트별 영향 분석

### 2.1 입력 계약 / MCP Tool 시그니처 — `DesignReferenceTool`

```java
public DesignAnalysisResult analyzeDesignReference(
        String referencePath, @Nullable String pageRange, @Nullable String featureType)
```

- `referencePath`는 로컬 파일 경로 전제, `pageRange`는 PDF 1-based 페이지 범위 전제 — 둘 다 Figma에는 대응 개념이 없음(Figma는 fileKey + node ID 조합).
- 기존 시그니처를 억지로 재사용(예: `referencePath`에 Figma URL을 넣기)하면 파라미터 의미가 겹쳐 혼란스럽고, `ReferencePathValidator`(2.2절)가 그 값을 파일 경로로 검증하려다 실패한다.
- **현실적 설계**: 별도 Tool 메서드 신설이 맞다. 예: `analyzeFigmaReference(String figmaUrl, @Nullable String nodeId, @Nullable String featureType)`. `@Tool` description도 새로 작성 필요.
- **`figmaUrl`과 별도 `nodeId` 파라미터의 충돌 규칙이 정의돼 있지 않다(3차 검토 반영, 신규)**: Figma URL 자체에 `node-id` 쿼리 파라미터가 포함될 수 있어(§2.2의 URL 파싱 규칙 참고), URL에서 추출한 node ID와 별도 `nodeId` 인자가 서로 다른 값일 때의 처리가 필요하다. 후보는 3가지다: ①URL에서만 node ID를 추출하고 별도 인자는 무시 ②별도 `nodeId` 인자가 있으면 URL에는 node-id를 아예 허용하지 않음(둘 중 하나만 출처로 인정) ③**둘 다 있으면 일치할 때만 허용하고 불일치 시 명시적으로 거부**(가장 모호함이 적어 권장). 어느 쪽이든 URL의 `1-2` 형식과 Figma API가 쓰는 `1:2` 형식 간 정규화도 입력 검증 단계에 포함해야 한다.
- `createScreenSpecification`이 받는 `designAnalysisId`는 조회 시 `DesignAnalysisResult`만 필요로 하므로, 신규 Tool이 이 ID 규격만 지키면 이 지점은 변경 불필요(2.6절 참고).

### 2.2 보안 경계 — `ReferencePathValidator`는 전혀 적용되지 않는다

현재 `ReferencePathValidator`는:
- `Path.of(referencePath).toRealPath()` + `allowed-paths` 화이트리스트로 **로컬 파일시스템 범위**를 강제
- 확장자 화이트리스트(`png/jpg/jpeg/pdf`) + **매직 바이트 검증**으로 확장자 위장을 차단
- 파일 크기 상한 검증

이건 "임의 파일 읽기"를 막기 위한 통제이며 **URL 입력에는 개념 자체가 적용되지 않는다.** Figma 경로를 지원하려면 **완전히 새로운 보안 계층**을 설계해야 한다:

1. **URL 검증은 "정규식 화이트리스트"가 아니라 "사용자 URL을 외부 요청에 아예 쓰지 않는 구조"가 핵심(2차 검토 반영, 정정)**: 정규식 매칭만으로는 파싱 경계가 모호해질 수 있다(예: 정규식이 host 파트를 느슨하게 잡으면 `https://www.figma.com.evil.com/...` 류 우회 가능). 권장 구조:
   1. `figmaUrl`을 **URI로 파싱**한다(`java.net.URI`).
   2. 파싱된 URI의 `scheme`(`https`)과 `host`(`www.figma.com`)를 **정확히(equals) 비교**한다 — 접두사/포함(contains) 매칭 금지.
   3. 경로에서 **fileKey/nodeId만 추출**하고, URL 자체는 버린다.
   4. **사용자가 제공한 URL 문자열은 어떤 경우에도 HTTP 요청 대상으로 다시 사용하지 않는다** — 오직 추출된 fileKey/nodeId만 애플리케이션이 고정한 API 호출에 파라미터로 넣는다.
   5. 실제 HTTP 요청은 애플리케이션이 상수로 고정한 `https://api.figma.com`(§2.3의 `FigmaApiClient.BASE_URL`)에만 보낸다.
   - **제품 범위 결정 필요**: Figma 공식 문서(`https://developers.figma.com/docs/rest-api/file-endpoints/`)는 파일 URL을 `/:file_type/:file_key/:file_name` 형태로 설명한다. `file_type`이 `file`/`design` 외에 다른 값(예: 팀 라이브러리, FigJam 등)도 존재할 수 있으므로, **어떤 `file_type`까지 지원 범위로 명시할지**(`file`/`design`만 허용, 그 외 거부) 제품 결정이 필요하다.
2. **리다이렉트 미추적**: 외부 응답이 내부망 주소로 리다이렉트시키는 시나리오 차단.
3. **액세스 토큰 관리 — 인증 모델별로 분리(2차 검토 반영)**: §1-1에서 정한 인증 모델에 따라 저장 방식이 달라진다.
   - **서버 PAT / Plan Access Token(단일 서버 토큰 모델)**: `globals.properties`나 코드에 하드코딩 금지(eGovFrame 보안 규칙과 동일 원칙), 환경변수 또는 외부 Secret Manager로만 주입.
   - **사용자별 OAuth 토큰**: 환경변수 방식 자체가 성립하지 않는다 — 사용자마다 다른 토큰이므로 **암호화된 저장소**(DB 컬럼 암호화 또는 전용 시크릿 스토어)에 사용자와 바인딩해 저장하고, 갱신(refresh)·폐기(revoke)를 지원해야 한다.
   - **공통**: 에러 메시지·로그에 토큰 원문이 노출되지 않도록 마스킹, 요청 스코프는 최소 권한(`file_content:read` 등)으로 제한.
4. **fileKey 화이트리스트 — 배포 형태에 따라 "선택"이 아니라 "필수"일 수 있다(3차 검토 반영, 정정)**: `allowed-paths`가 로컬 디렉터리를 제한하듯, 특정 팀/프로젝트의 Figma 파일 키만 허용할지는 §1-0의 배포 토폴로지에 종속된다.
   - 사용자별 로컬 배포(§1-0 첫 행)라면 선택 사항으로 남겨도 무방하다.
   - **중앙 단일 서버 토큰(PAT) 모델(§1-0 두 번째 행)에서는 사실상 필수 통제다** — §2.4에서 다루는 "캐시가 Figma 접근 권한 검사를 우회하는 문제"와 직결되므로, 화이트리스트가 없으면 서버 PAT가 접근 가능한 모든 Figma 파일을 어떤 MCP 사용자든 조회할 수 있게 된다.

원문서가 "입력 계약 자체가 바뀐다"고 짧게 언급한 부분이, 실제로는 **보안 검증 계층을 통째로 새로 설계·구현**해야 한다는 뜻임을 구체화한다.

### 2.3 `VisionAnalysisClient` 인터페이스 — 재사용 불가, 병행 경로 필요

```java
public interface VisionAnalysisClient {
    UiDesignSpec analyze(VisionAnalysisRequest request); // request.images() 필수
    String providerId();
    String modelId();
}
```

- `AbstractChatVisionAnalysisClient`는 `images` 바이트 배열을 `ChatClient.prompt().user(...).media(...).call().entity(UiDesignSpec.class)`로 vision LLM에 보내는 패턴에 고정돼 있다.
- Figma의 핵심 강점(옵션 E 원문의 ①③)은 "모델이 이미지를 보고 추측하는 게 아니라, API가 반환하는 **레이어 트리 JSON을 그대로 읽는 것**"이다. 이 강점을 살리려면 Figma 노드 JSON을 다시 이미지로 렌더링해 vision 모델에 태우면 안 되며(그러면 §4 ①에서 정리한 "Figma 문서 모델의 권위 있는 기하 정보를 그대로 읽는다"는 강점이 사라지고 다시 추정 기반으로 퇴행한다), **JSON → `UiDesignSpec` 직접 매핑 로직**이 필요하다.
- 따라서 `VisionAnalysisClient` 인터페이스에 억지로 끼워 맞추기보다, **별도의 신규 서비스**(예: `FigmaDesignSpecMapper`)를 두고 다음을 새로 구현해야 한다:
  - `absoluteBoundingBox`(x,y,width,height) → `LayoutSpec.density`/`formColumnLayout`/`actionPlacement` 등 **이산 값으로 변환하는 규칙**(이번에 이미 완료한 "이산 버킷" 설계와 유사한 매핑 로직이 Figma 좌표 버전으로 다시 필요)
  - 레이어 `type`(FRAME/TEXT/COMPONENT/INSTANCE 등) + 레이어 이름 → `ComponentSpec`/`UiFieldRole` 매핑 규칙(휴리스틱 또는 명명 컨벤션 파싱)
  - 레이어 이름이 "Rectangle 34" 같은 기본값일 때의 **폴백**: `UiDesignSpec.uncertainties`로 떨어뜨리는 처리 — 이 부분은 `ScreenSpecAssembler`가 이미 `uncertainties → SpecIssue(WARNING)` 변환을 하고 있어 **재사용 가능**(2.6절 참고).
- 즉 "시맨틱 분류가 공짜로 딸려온다"(원문 ③)는 **레이어 명명이 실제로 잘 관리된 파일에 한해서만** 참이고, 이를 판별·매핑하는 **신규 규칙 엔진 개발**이 필요하다는 점에서 "공짜"라는 표현은 과장이다.

#### 2.3-1. `FigmaApiClient` 운영 통제 — rate limit·타임아웃·재시도 등이 영향 분석에서 빠져 있었다(3차 검토 반영, 신규, 중요도: 중간)

Figma REST API에는 rate limit이 적용된다(Figma 공식 문서: `https://developers.figma.com/docs/rest-api/rate-limits/`). 지금까지의 분석은 `FigmaApiClient`를 "HTTP 클라이언트 하나 추가" 정도로만 다뤘으나, 실제로는 다음 운영 통제가 함께 설계돼야 한다:

- **연결·응답 타임아웃**: 기존 `DesignVisionProperties.timeoutSeconds`(vision 경로용)와 별도로 Figma 호출용 타임아웃 정의.
- **429 응답과 `Retry-After` 헤더 처리**: rate limit 초과 시 무작정 실패시키지 않고 헤더가 지시하는 대기 후 재시도할지 결정.
- **지수 백오프 + 최대 재시도 횟수**: 기존 `DesignVisionProperties.maxAttempts`(vision 경로용)와 같은 급의 설정이 Figma 경로에도 필요.
- **응답 JSON 크기 및 최대 노드 수 제한**: 대형 Figma 파일을 전체 트리로 읽으면 응답 크기와 §2.3의 매핑 비용(`FigmaDesignSpecMapper`)이 함께 커진다.
- **`depth` 기본 상한 적용, 전체 파일 대신 지정 node 우선 조회**: `GET /v1/files/{fileKey}` 대신 가능하면 `GET /v1/files/{fileKey}/nodes?ids=...`로 필요한 노드만 조회.
- **401/403/404/429 오류의 안전한 MCP 오류 변환**: Figma API 원본 오류 메시지를 그대로 노출하지 않고(토큰 유출 위험), MCP 클라이언트(Claude Desktop)에 안전한 형태로 변환.
- **구조화 로그에 토큰·fileKey 미포함**: §2.2의 토큰 마스킹 원칙을 로그 전반에 일관 적용.
- **"장애 시 vision 경로로 자동 폴백"은 독립적으로 성립하지 않는 조건부 폴백이다(4차 검토 반영, 정정)**: vision 분석에는 **이미지 바이트**가 입력으로 필요하다(`AbstractChatVisionAnalysisClient`, §2.3). 그런데 Figma API 자체가 장애이거나 rate limit에 걸린 상태라면, 애초에 Figma 노드를 이미지로 export하는 API 호출도 함께 실패하므로 "Figma API 장애 → vision으로 자동 전환"은 **입력을 구할 수 없어 성립하지 않는다.** 폴백이 가능한 경우는 다음처럼 **대체 이미지 입력이 이미 확보돼 있을 때뿐**이다:
  - 사용자가 Figma URL과 별도로 PNG/JPEG/PDF를 함께 제공한 경우
  - 이전에 안전하게 캐시해 둔 Figma export 이미지가 있는 경우(단, 이 캐시 자체도 §2.4의 캐시 권한 우회 문제와 동일한 통제가 필요)
  - 호출 전에 별도 경로로 이미 확보해 둔 렌더 이미지가 있는 경우
  - 위 셋 중 어느 것도 없다면 Figma 장애 시에는 **명시적으로 실패**시키는 것이 유일하게 안전한 기본 동작이며, "자동 폴백"을 정책 옵션으로 검토하려면 이미지 입력 확보 방법을 먼저 설계해야 한다.

이 항목들은 §5의 POC(성능·rate limit 검증)와 함께 확인해야 실사용 가능 여부를 판단할 수 있다.

### 2.4 캐싱/식별자 모델 — `DesignAnalysisResult`, `AI_DESIGN_ANALYSIS` 테이블

```sql
CREATE TABLE AI_DESIGN_ANALYSIS (
    ANALYSIS_ID, SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION, RESULT_JSON, CREATED_AT,
    UNIQUE KEY (SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)
)
```

- 캐시 키 `SOURCE_HASH`는 **파일 바이트의 SHA-256**(`DesignReferenceAnalysisService.sourceHash`)이다. Figma는 파일 바이트가 없으므로 이 캐시 무효화 전략이 그대로 적용되지 않는다.
- **기존 파일 경로에도 이미 있던 캐시 키 결함(신규 발견, Figma와 무관하게 존재)**: `sourceHash(Path path, String pageRange)`(`DesignReferenceAnalysisService.java:135`)는 파일 바이트와 `pageRange`만 해싱하고, 실제 분석 프롬프트에 들어가는 `featureType`(`DesignReferenceAnalysisService.java:112`, `analyzeAndSave` 호출부)은 캐시 키에 **포함되지 않는다.** 즉 동일 파일을 `featureType=crud`로 먼저 분석한 뒤 `featureType=board`로 재분석해도, 캐시가 첫 결과를 그대로 반환할 수 있다. 이건 Figma 도입과 무관하게 **현재 코드에 이미 존재하는 버그성 캐시 키 설계**이며, Figma 캐시 키를 새로 정의할 때는 이 결함을 반복하지 않아야 한다.
- Figma 파일은 디자이너가 계속 수정하는 살아있는 문서이므로, Figma 경로의 캐시 입력은 다음을 모두 포함해야 한다:
  1. `fileKey`, node ID
  2. **파일 version**(불변 ID) — Figma `GET file` 응답은 `version`과 `lastModified`를 함께 제공하는데, `lastModified` 같은 타임스탬프보다 **불변 `version` ID를 우선**하는 편이 캐시 무효화 의미가 명확하다.
  3. `featureType`(위에서 확인된 기존 결함을 Figma 경로에서는 반복하지 않기 위해 필수 포함)
  4. 매퍼 규칙 버전(§2.3 `FigmaDesignSpecMapper`의 매핑 로직이 바뀌면 캐시도 무효화되어야 함)
  5. API 요청 옵션(`depth`, `geometry` 등 — 같은 노드라도 요청 옵션에 따라 응답 상세도가 달라짐)
  6. 하이브리드(§2.5 (b) 병행 모델) 채택 시: 보정에 사용한 vision LLM의 provider/model/promptVersion
- **캐시 적중이 Figma 접근 권한 검사를 우회할 수 있다(3차 검토 반영, 신규 — 중앙 다중 사용자 배포에 한정)**: 위 캐시 키(fileKey+nodeId+fileVersion+…)만으로 캐시를 조회하면, **§1-0의 "중앙 다중 사용자 서버" 배포**에서 사용자 A가 먼저 분석해 캐시에 저장한 결과를, Figma상 해당 파일을 볼 권한이 없는 사용자 B가 **같은 식별자만 알면 Figma API를 다시 호출하지 않고도** 그대로 받아볼 수 있는 권한 우회가 발생한다.
- **"여러 통제 중 최소 하나"는 충분하지 않다(4차 검토 반영, 정정)**: 아래 4가지 통제는 목적이 서로 다르며 하나가 다른 것을 대체하지 못한다.

  | 통제 | 막는 대상 | 막지 못하는 것 |
  |---|---|---|
  | user/tenant 캐시 파티셔닝(같은 파일이라도 사용자마다 별도 캐시) | **다른 사용자에게 결과가 노출**되는 것 | 그 사용자 본인의 Figma 접근 권한이 이후 **폐기(revoke)된 경우** — 파티셔닝만으로는 본인 캐시를 계속 볼 수 있음. **또한 이 통제는 현재 DB 스키마로는 구현할 수 없다(5차 검토 반영, 아래 참고)** |
  | 캐시 적중 시에도 매번 Figma 권한 재확인 | 권한이 사후에 폐기된 경우 | (파티셔닝과 반대로) 매 요청 API 호출 비용 발생 — §2.3-1의 rate limit과 상충 가능 |
  | fileKey 화이트리스트 | 중앙 단일 서버 토큰(공유 PAT/조직 토큰)이 **조회 가능한 파일 범위** 자체를 제한 | 화이트리스트에 있는 파일 내에서의 사용자 간 노출은 막지 못함(파티셔닝과 별개 목적) |
  | 공용 캐시 승격(공유 승인된 분석만 별도 공용 캐시로) | Figma 권한과 무관하게 **애플리케이션이 명시적으로 공유를 승인**한 결과만 공개 | 승인되지 않은 결과의 사용자 간 노출은 막지 못함(파티셔닝이 기본값이어야 함) |

  - 따라서 **사용자별 OAuth 모델**에서는 파티셔닝만으로 끝내지 말고 다음 중 하나를 명시적으로 결정해야 한다: ①캐시 적중 시 Figma 권한 재확인(비용 감수) ②짧은 권한 검증 TTL 적용(예: N분 경과 후 재확인) ③권한 폐기 이후에도 파생 분석(이미 만든 `UiDesignSpec`)을 보존할 수 있다는 **별도의 데이터 보존 정책**을 명시적으로 수립(보존 시 "권한이 있었던 시점의 스냅샷"이라는 점을 사용자에게 고지).
  - 단일 조직 토큰(중앙 단일 서버 토큰) 모델이라면 위 §2.2의 fileKey 화이트리스트를 필수로 강제해 애초에 조회 가능한 파일 범위를 제한.
  - **"권한 재확인"의 실제 구현에는 스코프 제약이 있다(4차 검토 반영, 신규)**: "파일 메타데이터 조회로 권한 확인"은 Figma API에서 `file_metadata:read`처럼 `file_content:read`와 **별도 스코프**가 필요할 수 있다(Figma File API 문서: `https://developers.figma.com/docs/rest-api/file-endpoints/`). 최소 스코프(`file_content:read`)만 사용하는 방침을 유지하려면, 별도 메타데이터 API 대신 **실제 파일/노드 조회 자체로 권한을 검증**(성공하면 권한 있음, 403이면 없음)하거나, 권한 확인을 위한 추가 스코프 요청을 명시적으로 문서화해야 한다.
  - 사용자별 로컬 배포(§1-0 첫 행, loopback 바인딩 검증 시)에서는 캐시를 공유하는 다른 사용자가 없으므로 이 문제가 발생하지 않는다.

- **tenant별 캐시 파티셔닝은 현재 유니크 키로 구현할 수 없다(5차 검토 반영, 신규, 중요도: 높음)**: 현재 스키마의 유니크 제약은 다음과 같다.
  ```sql
  UNIQUE KEY UK_DESIGN_ANALYSIS_CACHE (SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)
  ```
  (`DesignAnalysisRepository.java:36`) — 이 4개 컬럼 조합에 **tenant/사용자 구분이 전혀 없다.** 따라서 §2.4 위 표의 "user/tenant 캐시 파티셔닝"을 실제로 적용하려면, 서로 다른 tenant가 **완전히 동일한 fileKey/nodeId/version(및 분석 계약)**으로 Figma 파일을 분석할 경우 **같은 캐시 행으로 충돌**한다 — 즉 tenant A의 분석 결과를 tenant B가 캐시를 통해 그대로 받아보는, §2.4가 막으려던 바로 그 문제가 재발한다. 다음 중 하나를 결정해야 한다:
  1. **`TENANT_ID` 컬럼을 추가하고 유니크 키에 포함**(`UNIQUE(TENANT_ID, SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)`) — 가장 명시적이며 조회·감사 쿼리에도 유리해 권장안이다.
  2. tenant/user/access-scope를 `SOURCE_HASH` 입력 문자열 자체에 포함해 해싱(§2.4의 "정규화 문자열" 방식 확장) — 컬럼 추가는 피하지만 조회·감사 시 tenant로 직접 필터링할 수 없어 불리하다.
  3. tenant마다 물리적으로 다른 저장소(테이블/DB)를 사용 — 가장 강한 격리지만 운영 복잡도가 가장 크다.
  - **이 발견으로 §2.4 앞부분의 "기존 DB DDL 재사용 가능성" 평가는 중앙 다중 사용자(정확히는 "다중 신뢰 경계") 배포에서 더 낮게 평가해야 한다.** 사용자별 로컬 배포나 "단일 신뢰 경계"(§1-0)에서는 tenant 구분 자체가 불필요하므로 이 문제가 발생하지 않는다.

- **기존 캐시 저장 로직(`save()`)에 Figma 도입과 무관하게 존재하는 동시성 정합성 결함이 있다(5차 검토 반영, 신규 — Figma 경로에서 발현 확률 증가, 중요도: 높음)**: `DesignAnalysisRepository.save(...)`는 다음과 같이 구현돼 있다(`DesignAnalysisRepository.java:42-49`).
  ```sql
  INSERT INTO AI_DESIGN_ANALYSIS
      (ANALYSIS_ID, SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION, RESULT_JSON)
  VALUES (?, ?, ?, ?, ?, ?)
  ON DUPLICATE KEY UPDATE RESULT_JSON = VALUES(RESULT_JSON)
  ```
  동일 캐시 키에 대해 두 요청이 **동시에 캐시 미스**를 확인하면 다음 순서로 정합성이 깨질 수 있다:
  1. 요청 A와 B가 모두 `findExact(...)`에서 캐시 미스를 확인(둘 다 아직 분석 전).
  2. A가 먼저 분석을 끝내고 `ANALYSIS_ID="A"`로 INSERT.
  3. B가 분석을 끝내고 `ANALYSIS_ID="B"`로 같은 유니크 키에 INSERT를 시도 → 유니크 키 충돌 → `ON DUPLICATE KEY UPDATE`가 발동하지만 **`RESULT_JSON`만 B의 결과로 덮어쓰고, DB 행의 물리적 PK(`ANALYSIS_ID`)는 여전히 A로 남는다.**
  4. 이후 `findExact(...)`(캐시 조회)는 그 행을 읽어 `RESULT_JSON`을 역직렬화하므로 **JSON 내부의 `analysisId`는 "B"**를 반환하지만, 그 "B"라는 ID로 `findById("B")`를 호출하면 DB에 그런 PK가 없어 **조회 실패**한다.
  - Figma 경로는 API 호출 지연(§2.3-1의 타임아웃·재시도)이 있어, 동일 입력에 대한 **동시 캐시 미스 확률이 vision 경로보다 커질 수 있다** — 이 문서가 다루는 "기존 코드에 이미 있던 결함"(§2.4의 `featureType` 누락과 같은 종류) 중에서도 Figma 도입이 발현 빈도를 높이는 사례다.
  - 영향 분석에 다음을 반영해야 한다:
    - 저장 시 **원자적 upsert**로 바꾸되, 유니크 키 충돌 시 "기존 행을 그대로 반환"하는 계약으로 정정(먼저 저장된 쪽이 확정 승자가 되고, 나중 요청은 자신이 계산한 결과를 버리고 기존 행을 재사용).
    - DB의 물리적 PK(`ANALYSIS_ID`)와 `RESULT_JSON.analysisId`가 항상 일치함을 보장.
    - 동일 캐시 키에 대한 **동시 분석 요청 테스트**, 그리고 5차 검토 항목 1(tenant 파티셔닝 도입 시)의 **tenant가 다른 동일 입력의 충돌 테스트**를 §2.6의 테스트 계획에 추가.

- `DesignAnalysisResult.sourcePath`/`pageRange` 필드는 "로컬 경로/PDF 페이지"라는 의미가 고정돼 있어, Figma URL을 그대로 넣으면 필드 의미가 왜곡된다. **`sourceType`(FILE/FIGMA) 구분 필드 추가** 등 `DesignAnalysisResult` JSON 모델 확장이 필요.
- record는 컴팩트 생성자로 null 방어를 하는 패턴이라 필드 추가 자체의 구현 난이도는 낮지만, `RESULT_JSON`에 이미 저장된 과거 레코드(신규 필드 없음)를 Jackson이 역직렬화할 때 기본값으로 안전하게 채워지는지 하위 호환 검증이 필요하다.
- **"DB DDL 변경 불필요"는 가능성이지 확정된 결론이 아니다(2차 검토 반영, 정정)**: `SOURCE_HASH` 컬럼(`VARCHAR(64)`)이 "파일 바이트 해시"여야 한다는 제약은 없으므로, `fileKey + nodeId + version + 분석 계약(위 1~5)`을 정규화한 문자열을 SHA-256으로 해싱하면 기존 컬럼·`UNIQUE KEY(SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)` 제약 자체는 재사용할 수 있다(`DesignAnalysisRepository.java:28`).
  - 그런데 `PROVIDER_ID`/`MODEL_ID`/`PROMPT_VERSION` 3개 컬럼은 **모두 `NOT NULL`**이다(`DesignAnalysisRepository.java:31`, `createTableIfNotExists`). 순수 Figma 매퍼 경로에는 "LLM model"도 "prompt"도 존재하지 않으므로, 이 3개 컬럼에 **무엇을 저장할지 명시적 계약을 먼저 정의**해야 DDL을 그대로 쓸 수 있다. 예:
    - `PROVIDER_ID = "figma"`
    - `MODEL_ID = "deterministic-mapper"`(배타적 모델, §2.5 (a)) 또는 하이브리드 시 실제 보정에 쓴 LLM 모델명(§2.5 (b))
    - `PROMPT_VERSION = "mapper-v1"` 또는 더 일반화된 `analysisContractVersion`으로 컬럼 의미 자체를 재정의
  - 이 계약을 정의하지 않으면 §2.6-b에서 지적한 "매퍼 버전을 `promptVersion`에 욱여넣으면 의미가 왜곡된다"는 문제가 그대로 재발한다. 즉 컬럼 재사용 자체는 가능하지만, **재사용하려면 컬럼 의미의 명시적 재정의가 선행 조건**이라는 점에서 "DDL 변경 불필요"는 **미완성 결론**이다. 이 계약을 명확히 정의하지 못하면 컬럼명을 일반화하는 마이그레이션(예: `MODEL_ID`→`ANALYSIS_ENGINE_ID`)이 대안이 된다.
  - `sourceType`도 `RESULT_JSON` 내부(도메인 모델)에만 추가하면 테이블 물리 스키마 자체는 그대로 둘 수 있다는 점은 유효하다. 따라서 이 항목은 "**캐시 식별자 계약과 `DesignAnalysisResult` JSON 모델 확장 + 기존 NOT NULL 3컬럼의 Figma 저장값 계약 정의. 계약을 못 정하면 컬럼 일반화 마이그레이션 필요**"로 정정한다.

### 2.5 설정(`DesignVisionProperties`) — provider 개념의 모순

- 현재 `provider: disabled|openai|ollama`는 "이 앱이 쓰는 vision LLM이 무엇인가"를 뜻한다. Figma는 LLM이 아니라 **API 클라이언트**이므로, `provider=figma`를 그대로 추가하면 "그럼 레이어명이 나빠서 시맨틱 보정이 필요할 때 vision LLM은 뭘 쓰는가"라는 모순이 생긴다.
- 두 가지 설계 방향 중 결정이 선행되어야 한다:
  - (a) **배타적**: Figma 모드에서는 vision LLM을 아예 안 쓰고 매핑 규칙만으로 처리(레이어명 나쁘면 uncertainties로 남김)
  - (b) **병행**: Figma로 좌표·구조를 얻고, 이름이 불명확한 노드만 별도로 vision LLM(기존 `VisionAnalysisClient`)에 재질의해 보정 — 파이프라인이 2단계로 늘어남(옵션 C가 겪었던 "결국 하이브리드 2단계"와 같은 복잡도)
- 신규 시크릿: 저장 방식은 §1-1에서 정한 인증 모델에 따라 갈린다(§2.2 3번 항목과 동일) — 서버 PAT/Plan Access Token이면 `figma-access-token`(환경변수 또는 Secret Manager), 사용자별 OAuth면 사용자-토큰 암호화 저장소가 별도로 필요하다. `figma-api-base-url`은 인증 모델과 무관하게 `https://api.figma.com` 고정값(오버라이드 불가 권장, §2.2 URL 검증 구조와 동일 원칙). `allowed-paths`처럼 "허용 리소스" 화이트리스트를 Figma에도 도입할지 결정 필요.

### 2.6 하위 파이프라인 — "영향 없음"의 범위 세분화 (정정: 경로별로 다르다)

초판은 "하위 파이프라인은 영향 없음"으로 뭉뚱그렸으나, 실제로는 **경로가 3개로 갈리고 그중 하나는 변경이 필요하다.**

**(a) 직접 `analysisId` 조회 경로 — 기능적으로는 영향 없음, 그러나 중앙 다중 사용자 배포에서는 접근 통제 관점이 별도로 존재한다(4차 검토로 평가 정정)**
- `createScreenSpecification(..., designAnalysisId, ...)`(`DesignReferenceTool.java:56`)는 `designReferenceAnalysisService.get(designAnalysisId)`로 `DesignAnalysisResult`를 ID로 바로 조회해 그 안의 `uiSpec`만 꺼내 쓴다.
- `ScreenSpecAssembler.assemble(...)`과 `ScreenSpecificationService.create(...)`는 **`UiDesignSpec` 레코드 자체만 소비**하며 출처(OpenAI/Ollama/Figma)를 모른다. `uncertainties()` → `SpecIssue(WARNING)` 변환도 범용이라 Figma 매핑 실패 케이스를 그대로 흡수한다.
- **Figma 매핑 결과가 `UiDesignSpec` 스키마를 채우기만 하면 기능적으로는 정말로 변경이 필요 없다는 판단은 그대로 유지한다.** 다만 이는 "동작하는가"에 대한 평가이며, "누가 조회할 수 있어야 하는가"에 대한 평가가 아니다.
- **객체 단위(tenant/owner) 인가가 이 경로 전반에 없다(4차 검토 반영, 신규 — 중앙 다중 사용자 배포에 한정, 중요도: 높음)**: `DesignReferenceAnalysisService.get(analysisId)`(`DesignReferenceAnalysisService.java:55`)는 소유자·tenant 확인 없이 ID만 일치하면 결과를 반환한다. 이 ID는 서버가 발급한 UUID이지만, **UUID를 안다는 것과 "이 사용자가 이 결과를 볼 권한이 있다"는 것은 별개**이며, ID가 로그·URL·대화 기록 등으로 다른 사용자에게 노출될 가능성을 배제할 수 없다. §1-0의 "중앙 다중 사용자 서버" 배포에서는 다음 경로 **전부**가 같은 문제를 갖는다:
  - `DesignReferenceAnalysisService.get(analysisId)`
  - `DesignReferenceTool.createScreenSpecification(..., designAnalysisId, ...)`
  - `findReusableCandidates(...)`(§2.6-b-1의 RAG 노출과는 별개로, 후보 확정 이후 실제 상세 조회 단계에서도 재검증 필요)
  - `getScreenSpecification(screenSpecificationId)`
  - `approveScreenSpecification(screenSpecificationId)`
  - `reviseScreenSpecification(specification)`
  - `buildFullCrudPrompt`/`buildMasterDetailPrompt` 등 코드 생성 단계에 전달되는 `designReferenceId`/`screenSpecificationId`
  - 이를 해소하려면 `DesignAnalysisResult`와 `ScreenSpecification`에 `tenantId`/`ownerId`/`visibility` 필드를 추가해야 한다. 구현 방식은 두 가지가 있으며, **저장소 조회 조건절에 포함하는 방식(`WHERE id=? AND tenant_id=?`)이 조회·감사에 유리해 권장되지만, 유일하게 안전한 방식이라고 단정할 필요는 없다(5차 검토 반영, 정정)**:
    1. **저장소 조건절 포함(권장)**: `WHERE id=? AND tenant_id=?` 형태로 쿼리 자체에 보안 주체 조건을 포함.
    2. **서비스 계층의 "조회 후 인가"도, 다음을 지키면 안전할 수 있다**: ①결과 내용을 반환하기 전에 반드시 인가 검사를 통과시킨다 ②미존재(404)와 접근 거부(403)를 **동일한 응답**으로 처리해 "이 ID가 존재하는지"조차 추론하지 못하게 한다 ③로그·예외 메시지에 조회된 객체의 정보를 노출하지 않는다 ④이 인가 검사를 개별 호출부마다 산발적으로 두지 않고 **공통 서비스/정책(예: 인터셉터, AOP)**으로 강제해 인가 누락을 구조적으로 방지한다.
  - 이는 **단건 PK 조회**(`get(analysisId)` 등)에 한정된 이야기이며, §2.6-b-1의 **RAG 유사도 검색(topK)**은 여전히 "검색 요청 자체에 필터 포함"이 필요하다는 결론이 유지된다 — 랭킹 상위 N개를 뽑는 연산은 사후 필터링 시 결과 자체가 비어버리는 문제(§2.6-b-1)가 있어 단건 조회와 성격이 다르다.
  - 사용자별 로컬 배포(§1-0 첫 행, loopback 바인딩 검증 시)와 중앙 단일 사용자 서버(§1-0 두 번째 행)에서는 애초에 다른 사용자가 없으므로 이 문제가 발생하지 않는다.

**(b) 시맨틱 재사용 경로 — 변경 필요(초판이 놓친 부분, 2차 검토로 계약 자체도 재수정)**
- `findReusableCandidates(query, expectedArchetype, topK)`(`DesignReferenceAnalysisService.java:71`)는 RAG 유사도 검색으로 후보를 찾은 뒤, **현재 활성 `VisionAnalysisClient`의 `providerId()`/`modelId()`와 `properties.getPromptVersion()`**을 저장된 모든 분석 결과의 `provider`/`model`/`promptVersion`과 비교해 `reusable` 여부·불일치 사유를 계산한다(`DesignReferenceAnalysisService.java:91-99`).
- Figma로 생성한 `DesignAnalysisResult`를 이 저장소에 함께 넣으면:
  - `provider="figma"`인 레코드는 현재 활성 `VisionAnalysisClient.providerId()`(`openai`/`ollama`)와 **항상 불일치**로 판정되어 재사용 후보에서 사실상 배제되거나, 반대로
  - Figma 전용 매퍼의 "버전"(§2.4에서 정의한 매퍼 규칙 버전)을 표현할 필드가 없어 `promptVersion` 필드에 억지로 욱여넣게 되면 의미가 왜곡된다.
- **정정(2차 검토 반영): "캐시 동일성"과 "시맨틱 재사용 호환성"은 서로 다른 계약이며, fileKey를 재사용 호환성 기준에 넣으면 안 된다.**
  - `findReusableCandidates`의 목적은 **다른 디자인 참조에서 얻은, 의미상 유사한 화면 분석도 찾아내는 것**이다(예: A팀 Figma 파일의 "회원목록" 화면 분석을, B팀이 새로 만드는 유사한 "회원목록" 화면에 재사용 후보로 제안). fileKey를 호환성 조건에 넣으면 **같은 Figma 파일에서 나온 결과만 재사용 가능**해져, 시맨틱 검색 기능 자체의 의미가 크게 줄어든다(초판 §2.6-b의 "fileKey+매퍼버전 비교" 제안은 이 목적과 어긋나므로 폐기한다).
  - 두 계약을 다음과 같이 분리해야 한다:

    | 계약 | 구성 요소 | 용도 |
    |---|---|---|
    | **캐시 동일성**(같은 입력이면 재분석 생략) | fileKey + nodeId + fileVersion + featureType + 분석 계약(§2.4의 1~5) | `analyzeFigma(...)` 호출 시 API 재호출 여부 판단 |
    | **시맨틱 재사용 호환성**(다른 참조에서도 재사용 후보 판정) | `sourceType` + `UiDesignSpec` 스키마 버전 + 매퍼 버전 + featureType/archetype 호환성 | `findReusableCandidates`의 `reusable`/불일치 사유 계산 |
    | **출처 추적 정보**(호환성 판정에는 미사용) | fileKey, nodeId | 감사 로그·"이 분석이 어느 Figma 파일에서 왔는지" 추적용으로만 결과에 포함 |

  - 즉 fileKey/nodeId/fileVersion은 **캐시 동일성에는 필요하지만, 시맨틱 재사용 호환성 판정에는 원칙적으로 포함하지 않는다.**
- 따라서 `findReusableCandidates`의 호환성 판정 로직은 **Figma 출처 레코드를 인식하고 위 표의 "시맨틱 재사용 호환성" 계약(provider/model/promptVersion 3종 비교 대신, `sourceType`+스키마버전+매퍼버전+featureType/archetype 비교)으로 분기**하도록 수정이 필요하다. 이 지점은 "영향 없음"이 아니라 **변경 필요** 항목으로 분류한다.

**(b-1) RAG·시맨틱 재사용 저장소에는 사용자/조직 격리가 전혀 없다(3차 검토 반영, 신규 — 중앙 다중 사용자 배포에 한정, 중요도: 높음)**
- `ingestBestEffort(DesignAnalysisResult result)`(`DesignReferenceAnalysisService.java:146`)는 모든 분석 결과를 예외 없이 동일한 `"design_analysis"` 타입으로 RAG에 인제스트한다. 소유자·팀·공개범위를 구분하는 필드가 없다.
- `findSemanticCandidates(query, topK)`(`DesignReferenceAnalysisService.java:60`)와 `findReusableCandidates`가 호출하는 `ragService.search(...)`도 tenant·사용자·조직·공개범위 조건 없이 **전체 RAG 저장소를 대상으로 검색**한다.
- **§1-0의 "중앙 다중 사용자 서버" 배포**에서는 이 구조가 그대로 정보 노출로 이어진다: A팀이 비공개 Figma 파일로 만든 화면 분석이, 전혀 관계없는 B팀이 `findReusableCandidates`를 호출했을 때 재사용 후보로 노출될 수 있다. Figma 쪽 접근 권한과 무관하게, **분석 결과가 한 번 RAG에 들어가면 그 이후로는 아무 접근 통제 없이 검색된다.**
- 따라서 §2.6-b에서 정의한 "시맨틱 재사용 호환성" 계약에는 **의미적 호환성보다 먼저 다음 접근 범위 필드가 게이트로 적용되어야 한다**:
  - `tenantId` 또는 조직 ID
  - 소유자(owner) 또는 생성 주체
  - `visibility`: `PRIVATE` / `TEAM` / `SHARED`
  - 재사용을 허용한 범위(공유 승인 여부)
  - 현재 요청자의 접근 권한(요청자가 해당 tenant/team에 속하는지)
- 즉 판정 순서는 "①이 후보를 볼 권한이 있는가(접근 통제) → ②의미적으로 호환되는가(§2.6-b 표)"여야 하며, 현재 구조는 ①이 아예 없이 ②만 수행한다.
- **접근 필터는 "검색 후 거르기"가 아니라 "검색 전에 적용"돼야 한다(4차 검토 반영, 구체화, 중요도: 중간)**: 현재 `RagService.search(String query, int topK)`(`RagService.java:289`)는 `SearchRequest.builder().query(query).topK(topK).build()`로 **필터 없이 먼저 topK를 조회**한다. 접근 통제를 여기에 얹을 때 "검색 결과를 받은 뒤 Java 코드에서 tenant가 다른 문서를 걸러내는" 방식으로 구현하면, 정보 노출 자체는 막을 수 있어도 **상위 topK 결과가 다른 tenant의 문서로 채워져 정작 현재 사용자와 관련된 문서가 잘려나가는 문제**가 생긴다(예: topK=5인데 상위 5개가 전부 타 tenant 문서라면, 필터링 후에는 0개가 남는다).
  - 필요한 구조는 다음 순서다:
    1. 인제스트 시 `tenantId`/`ownerId`/`visibility`를 **Vector Store 메타데이터**에 저장(`RagService.ingestText(...)`와 `DesignReferenceAnalysisService.ingestBestEffort(...)`(`DesignReferenceAnalysisService.java:146`) 양쪽의 시그니처 변경 필요).
    2. `similaritySearch` 요청 자체에 **접근 범위 필터 표현식**을 적용(Spring AI `SearchRequest`의 필터 기능 활용).
    3. **필터링된 집합 안에서** topK를 계산 — 필터를 검색 이후가 아니라 검색 요청에 포함.
    4. 결과 반환 전 §2.6-a에서 정의한 객체 단위 인가로 한 번 더 재검증(방어적 이중화).
  - 따라서 `RagService.ingestText()`/`search()`의 **시그니처 변경**도 이 문서의 영향 매트릭스에 포함해야 한다(§3에 신규 행으로 반영) — 이는 Figma 전용 변경이 아니라 `RagService`를 사용하는 다른 기능(문서 RAG 등)에도 영향을 줄 수 있는 더 넓은 범위의 변경이므로 별도 검토가 필요하다.
- **Vector Store 메타데이터 필터만으로는 부족하다 — Redis 청크 추적 키에도 tenant 네임스페이싱이 필요하다(5차 검토 반영, 신규, 중요도: 중간)**: `RagService`는 재인제스트 시 이전 청크를 지우기 위해 Redis에 `CHUNK_IDS_PREFIX + docId`(`"chat:chunk-ids:" + docId`) 키로 청크 ID 목록을 관리한다(`RagService.java:61,106,116-123`). 이 키는 **`docId`만으로 구성되며 tenant 구분이 없다.** 서로 다른 tenant가 우연히(또는 Figma의 `analysisId`처럼 애플리케이션이 생성하는) 같은 `docId`를 쓰게 되면, **한 tenant의 재인제스트(`deleteOldChunks` → `ingestText`)가 다른 tenant의 청크를 삭제**해버릴 수 있다 — 이는 정보 노출이 아니라 **가용성/데이터 손실** 문제다.
  - 따라서 Vector Store 메타데이터 필터(위 3단계 구조)만으로는 불충분하며, 다음 전부를 tenant 범위로 네임스페이스해야 한다:
    1. Redis 청크 추적 키 — 예: `chat:chunk-ids:{tenantId}:{docId}`
    2. 논리적 `docId` 자체(같은 파일이라도 tenant마다 다른 docId가 되도록)
    3. Vector Store 메타데이터(위에서 이미 다룸)
    4. 삭제·재인제스트 조건(`deleteOldChunks`가 tenant 경계를 넘어 삭제하지 않도록)
    5. 필요하다면 Vector Store document ID 자체
- 사용자별 로컬 배포(§1-0 첫 행, loopback 바인딩 검증 시)에서는 RAG 저장소 자체가 사용자 1인 전용이므로 이 문제가 발생하지 않는다.

**(c) `buildFullCrudPrompt` 이후 승인된 `ScreenSpecification` 소비 — 영향 없음**
- `approveScreenSpecification`/`reviseScreenSpecification` 이후 `buildFullCrudPrompt`/`buildMasterDetailPrompt`가 소비하는 것은 이미 확정된 `ScreenSpecification`이며, 그 이전 단계가 Figma였는지 vision이었는지와 무관하다.

**요약(4차 검토로 재정정)**: "하위 파이프라인 영향 없음"은 **기능적 관점(동작하는가)**에서는 (a)·(c)에 정확하고 (b)에는 적용되지 않는다. 그러나 **접근 통제 관점(누가 조회할 수 있는가)**에서는 중앙 다중 사용자 배포에 한해 (a)에도 신규 영향(객체 단위 인가)이 존재한다 — §3 매트릭스와 §5 선행 필요 사항에 (a)의 접근 통제 이슈와 (b)를 모두 별도 항목으로 반영한다.

### 2.7 문서/테스트 자산

- `McpConfig.java`의 `allToolCallbacks` 빈은 `DesignReferenceTool` 컴포넌트 자체를 이미 등록해뒀으므로, 같은 클래스에 메서드를 추가하는 형태라면 **Tool 등록 자체는 추가 작업 불필요**.
- `CLAUDE.md`의 "DesignReferenceTool 사용법" 섹션, `local-vision-design-reference-integration-review.md`의 10.5 "망분리 배포 가능성 — 구현 전 하드 게이트"(현재 OpenAI/Ollama만 다룸), `design-vision-tool-test-priority-detail.md`(현재 vision 경로 테스트만 다룸) 모두 Figma 경로가 추가되면 **신규 섹션이 필요**하다 — 특히 망분리 하드 게이트는 Figma도 외부 SaaS이므로 동일하게 적용되어야 한다.

---

## 3. 종합 영향 매트릭스

| 구성요소 | 영향 정도 | 사유 |
|---|---|---|
| **배포 토폴로지 결정 + loopback 바인딩 검증(로컬/중앙단일/중앙다중)** | **가장 높음(신규, 3차 검토 반영, 4차 검토로 loopback 검증 조건 추가 — 아래 인증·격리 항목 전체를 좌우하는 선결 변수)** | 사용자별 로컬 배포면 아래 인증·캐시·RAG 격리 문제 대부분이 발생하지 않으나, 현재 `application.yaml`에 `server.address` 지정이 없어 "로컬 실행"이 곧 "loopback 전용"을 보장하지 않음(§1-0) |
| **직접 `analysisId`/`screenSpecificationId` 조회 경로의 객체 단위(tenant/owner) 인가** | **높음(신규, 4차 검토 반영, 중앙 다중 사용자 배포 한정)** | `get`/`createScreenSpecification`/`getScreenSpecification`/`approveScreenSpecification`/`reviseScreenSpecification` 등 ID 기반 조회 전반에 소유자 확인이 없음(§2.6-a) |
| **`springai` MCP 서버의 사용자 인증·인가 체계** | **매우 높음(신규, 2차 검토 반영, 중앙 다중 사용자 배포 한정)** | `/mcp/**`가 현재 `permitAll()`(무인증) — 사용자별 OAuth를 택하면 Figma 인증보다 먼저 MCP 서버 자체의 사용자 신원 확인 체계를 새로 도입해야 함(§1-1-1) |
| **다중 사용자 인증·권한 모델(어떤 모델을 쓸지)** | **가장 높음(신규, 초판 누락)** | 단일 서버 토큰 / 사용자별 OAuth / Plan Access Token(베타) 중 선택에 따라 위 항목 발생 여부가 갈림(§1-1) |
| **캐시 적중의 Figma 접근 권한 우회 방지** | **높음(신규, 3차 검토 반영, 중앙 다중 사용자 배포 한정)** | 캐시 키만으로 응답하면 권한 없는 사용자가 캐시를 통해 Figma 파일 내용을 열람 가능(§2.4) |
| **tenant별 캐시 파티셔닝과 DB 유니크 키 불일치** | **높음(신규, 5차 검토 반영, 다중 신뢰 경계 배포 한정)** | 현재 `UNIQUE(SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)`에 tenant 구분이 없어 서로 다른 tenant의 동일 입력이 같은 캐시 행으로 충돌 — `TENANT_ID` 컬럼 추가(유니크 키 포함)가 권장안(§2.4) |
| **기존 캐시 저장 로직의 동시성 정합성 결함**(`ON DUPLICATE KEY UPDATE`) | **높음(신규, 5차 검토 반영 — Figma와 무관하게 기존 결함이나 Figma 지연으로 발현 확률 증가)** | 동시 캐시 미스 시 DB PK와 `RESULT_JSON.analysisId`가 불일치할 수 있음 — 원자적 upsert(기존 행 반환 계약)로 전환 필요(§2.4) |
| **RAG·시맨틱 재사용 후보의 tenant/visibility 격리** | **높음(신규, 3차 검토 반영, 중앙 다중 사용자 배포 한정)** | 현재 RAG 인제스트·검색에 소유자·공개범위 구분이 전혀 없어 `findReusableCandidates`가 타 팀 비공개 분석을 노출할 수 있음(§2.6-b-1) |
| `RagService.ingestText()`/`search()` 시그니처 변경 + Redis 청크 추적 키(`CHUNK_IDS_PREFIX+docId`) tenant 네임스페이싱 | **중간(신규, 4차·5차 검토 반영, Figma 전용이 아니라 `RagService` 공용 변경)** | 필터를 검색 후 적용하면 topK가 타 tenant 문서로 채워져 관련 문서가 누락될 수 있어 검색 요청 단계부터 필터 적용 필요. Redis 키가 `docId`만으로 구성돼 있어 tenant가 다른 동일 `docId`에서 한쪽의 재인제스트가 다른 쪽 청크를 삭제하는 데이터 손실 위험도 있음(§2.6-b-1). 문서 RAG 등 다른 `RagService` 사용처에도 영향 |
| `DesignReferenceTool` (Tool 시그니처) | **높음** | 신규 메서드 필요, 기존 파라미터 재사용 불가 |
| `ReferencePathValidator` (보안 검증) | **높음(신규 설계)** | 파일 기반 통제 무의미. URL을 요청 대상으로 재사용하지 않는 구조(URI 파싱+정확 비교) 신규 설계, 토큰 관리는 인증 모델별 분리(§2.2) |
| 기존 `VisionAnalysisClient`/`AbstractChatVisionAnalysisClient`/`OpenAiVisionAnalysisClient`/`OllamaVisionAnalysisClient` **구현 자체의 변경** | **없음~낮음(3차 검토로 하향 정정)** | §2.5 (a) 배타 모델이든 (b) 병행 모델이든, 기존 클래스는 그대로 두고 Figma 경로를 병렬로 추가하는 구조이므로 기존 구현을 고칠 필요는 원칙적으로 없음 |
| Figma 전용 분석 추상화·클라이언트·매퍼(`FigmaApiClient`/`FigmaDesignSpecMapper` 등) **신규 개발** | **높음** | JSON 직접 매핑이 핵심 강점이라 기존 vision 파이프라인에 끼워 맞추면 강점 상실 — 별도 신규 서비스로 구현해야 함(§2.3) |
| 하이브리드 오케스트레이션(§2.5 (b) 병행 모델 채택 시) | **채택 시 높음, 미채택 시 해당 없음** | Figma 결과와 vision LLM 보정을 잇는 2단계 파이프라인 신규 구축 필요(§2.5) |
| `findReusableCandidates` 시맨틱 재사용 판정 | **중간(신규, 초판 누락, 2차 검토로 계약 재정정)** | fileKey는 호환성 조건이 아니라 캐시 동일성/출처 추적 전용 — `sourceType`+스키마버전+매퍼버전+featureType/archetype으로 별도 계약(§2.6-b) |
| `DesignAnalysisResult` JSON 모델 / 캐시 식별자 계약 | **중간(DDL 재사용 가능성 있으나 컬럼 의미 계약 미확정)** | fileKey+node+version+featureType+매퍼버전 정규화 해싱은 가능하나, `PROVIDER_ID`/`MODEL_ID`/`PROMPT_VERSION` NOT NULL 컬럼의 Figma 저장값 계약을 먼저 정의해야 함(§2.4) |
| `DesignVisionProperties`(설정) | **중간** | provider 개념 모순 해소 설계 선행, 인증 모델별 시크릿 저장 방식 분리 |
| Figma API 운영 통제(타임아웃/429·Retry-After/백오프/depth 상한/오류 변환) | **중간(신규, 3차 검토 반영)** | rate limit·대형 파일 응답 크기·오류 변환·폴백 정책 등이 설계에서 빠져 있었음(§2.3-1) |
| `ScreenSpecAssembler`/직접 `analysisId` 소비/`buildFullCrudPrompt` 연동 | **없음** | UiDesignSpec만 소비, 출처 무관(§2.6-a, c) |
| MCP 등록(McpConfig) | **낮음** | 기존 컴포넌트에 메서드만 추가하면 등록은 자동 |
| 보안 검토·테스트 문서 | **중간** | 신규 섹션 추가(SSRF, 토큰 관리, 캐시 무효화, 매핑 폴백 테스트, 인증 모델, MCP 서버 인증) |
| **애플리케이션의 Figma 연결 인프라 자체** | **원문 평가와 달리 신규(중간~매우 높음, 인증 모델에 따라 가변)** | 세션의 Figma MCP는 앱과 무관 — REST API 클라이언트 또는 MCP 클라이언트를 `springai` 안에 새로 구축해야 함(§1) |

---

## 4. 원문 4개 주장 최종 평가

| 원문 주장 | 평가 | 근거 |
|---|---|---|
| ① 좌표 정확도 100%(D와 동급) | **조건부 정확(정정: "정확"에서 하향)** | Figma 문서 모델상의 기하 좌표(`absoluteBoundingBox`)는 추정 없이 얻을 수 있지만, 화면에 실제 렌더링되는 가시 영역과 100% 동일하다고 볼 수는 없다. `absoluteBoundingBox`는 변환 후 **기하** 바운딩 박스이고, 그림자·두꺼운 stroke 등을 포함한 실제 렌더링 범위는 `absoluteRenderBounds`가 담당하며 비가시 노드에서는 이 값이 `null`일 수 있다. 회전·clipping·instance·auto-layout·효과(effect)를 어떤 좌표 기준으로 해석할지도 별도로 정해야 한다(Figma 노드 타입 공식 문서: `https://developers.figma.com/docs/rest-api/file-node-types/`). |
| ② "아직 구현 전 목업" 단계에서 사용 가능 | **정확** | 배포 전 Figma 파일에도 이미 좌표 데이터 존재(D의 "배포된 URL 필요" 제약이 없음) |
| ③ 시맨틱 분류가 "공짜로" 딸려온다 | **과장 — 조건부** | 레이어 명명이 잘 관리된 파일에 한하며, 그 판별·매핑에는 신규 규칙 엔진 개발이 필요(2.3절) |
| ④ 인프라 부담이 가장 적다 | **부정확 — 세션과 앱의 혼동, 인증 모델 누락** | "이 세션에 연결된 Figma MCP"는 개발 대화 도구이지 배포된 `springai` 앱의 인프라가 아님. 앱은 REST API 클라이언트 등 신규 통합이 필요(§1)하며, 다중 사용자 인증 모델(§1-1)에 따라 부담이 "신규 HTTP 클라이언트 하나" 수준일 수도, "인증 서브시스템 신규 구축" 수준일 수도 있다 |

---

## 5. 만약 실제로 진행하기로 한다면 — 선행 필요 사항 (승인 필요)

5차례 외부 검토를 거치며 선행 결정 목록이 늘어났다. **1~3번이 이번(5차) 검토의 핵심 항목**이다 — 5차 검토는 "tenant 캐시 파티셔닝과 DB 유니크 키의 일치", "동시 캐시 저장 시 DB PK와 JSON `analysisId`의 정합성 보장" 두 가지를 최종 확정 전 필수 보완점으로 지목했다.

1. **[핵심/신규] tenant 캐시 파티셔닝과 DB 스키마 일치**: 현재 `UNIQUE(SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)`에 tenant 구분이 없어, "다중 신뢰 경계" 배포에서 tenant 파티셔닝을 캐시 우회 방지책으로 쓰려면 `TENANT_ID` 컬럼을 유니크 키에 추가(권장)하거나 `SOURCE_HASH` 입력에 tenant를 포함하거나 물리적으로 다른 저장소를 쓰는 3가지 중 하나를 결정해야 한다(§2.4).
2. **[핵심/신규] 캐시 저장 로직의 동시성 정합성 보장**: 현재 `save()`의 `ON DUPLICATE KEY UPDATE RESULT_JSON=VALUES(RESULT_JSON)`은 동시 캐시 미스 시 DB PK(`ANALYSIS_ID`)와 `RESULT_JSON.analysisId`가 불일치할 수 있는 기존 결함이다(Figma와 무관하게 존재하나 API 지연으로 발현 확률 증가). 원자적 upsert(기존 행 반환 계약)로 전환하고, 동시 분석 요청 테스트·tenant 간 충돌 테스트를 추가해야 한다(§2.4).
3. **[핵심/신규] RAG Redis 청크 추적 키의 tenant 네임스페이싱**: `CHUNK_IDS_PREFIX+docId` 키에 tenant 구분이 없어, 서로 다른 tenant가 같은 `docId`를 쓰면 한쪽의 재인제스트가 다른 쪽 청크를 삭제할 수 있다(정보 노출이 아니라 데이터 손실 위험). Redis 키·논리 docId·Vector Store 메타데이터·삭제조건·document ID를 모두 tenant 범위로 네임스페이스해야 한다(§2.6-b-1).
4. **배포 토폴로지 확정(신뢰 경계 기준) + loopback 바인딩 검증**: 사람 수가 아니라 **신뢰 경계**를 기준으로 "단일 보안 주체 / 여러 사용자가 하나의 공유 권한을 명시적으로 수용한 단일 신뢰 경계 / 사용자별 권한 분리가 필요한 다중 신뢰 경계" 중 실제 배포 형태를 확정(§1-0, 5차 검토로 분류 기준 정정). **"로컬 실행"이라는 사실만으로는 안전 근거가 되지 않는다** — 호스트 직접 실행이면 `server.address: 127.0.0.1`, 컨테이너 실행이면 앱은 컨테이너 내부 `0.0.0.0`에 바인딩하되 호스트 포트 게시를 `127.0.0.1:8080:8080`처럼 loopback으로 제한(5차 검토로 Docker 설명 정정), 중앙 서버라면 명시적 인증·방화벽·TLS 적용. **이 결정에 따라 위 1~3번, 아래 5~11번, §1-1의 인증 모델 결정이 실제로 필요한지 여부 자체가 갈린다.**
5. **직접 ID 조회 경로 전반의 tenant/owner 인가 설계**: "다중 신뢰 경계" 배포를 택한다면, `get(analysisId)`/`createScreenSpecification`/`getScreenSpecification`/`approveScreenSpecification`/`reviseScreenSpecification`과 코드 생성 단계의 ID 전달까지 포함해 인가를 설계 — 저장소 조건절 포함(권장) 또는 §2.6-a에서 정리한 4가지 조건을 지키는 서비스 계층 조회-후-인가 중 선택(5차 검토로 "저장소 조건절만이 유일한 안전 방식"이라는 단정 완화).
6. **캐시 파티셔닝과 권한 재확인의 역할 분리 설계**: "사용자 간 노출 방지"(파티셔닝, 1번과 연계)와 "권한 폐기 이후 접근 차단"(재확인/TTL/데이터 보존 정책)을 별개 통제로 설계(§2.4).
7. **RAG·시맨틱 재사용 후보의 tenant/visibility 격리 설계 — 검색 요청 단계부터**: `findReusableCandidates`가 접근 권한 없는 후보를 노출하지 않도록 `similaritySearch` 요청 자체에 필터를 포함(§2.6-b-1, 3번의 Redis 키 네임스페이싱과 함께 설계).
8. **캐시 적중 시에도 적용되는 Figma 접근 권한 검증 통제 조합 결정**: "권한 재확인 / 짧은 TTL / 데이터 보존 정책" 중 무엇을 채택할지, 최소 스코프(`file_content:read`)만으로 권한을 검증할 방법까지 확정(§2.4).
9. **Figma API rate limit·응답 크기·재시도 운영 정책 확정**: 타임아웃, 429/Retry-After 처리, 지수 백오프, depth 상한, 오류 변환(§2.3-1). **장애 시 vision 폴백은 "대체 이미지 입력이 이미 확보된 경우"에만 조건부로 가능**하다는 전제 하에 입력 확보 방법을 결정하지 않으면 폴백 미지원이 기본값.
10. **인증·권한 모델 결정 — MCP 서버 인증까지 포함**: 단일 서버 토큰(PAT/Plan Access Token) / 사용자별 OAuth 중 어느 모델을 쓸지(§1-1). **사용자별 OAuth를 고려한다면, Figma 인증 서브시스템보다 먼저 `springai` MCP 서버(`/mcp/**`) 자체의 사용자 인증·인가 체계 도입 여부부터 결정해야 한다**(§1-1-1 — 현재 `/mcp/**`는 `permitAll()`로 완전 무인증).
11. **"정확한 좌표"의 정의 확정**: `absoluteBoundingBox`(기하 바운드)를 쓸지, 가능한 경우 `absoluteRenderBounds`(실제 렌더 바운드)까지 함께 고려할지, 회전·clipping·instance·auto-layout·효과가 있는 노드를 어떤 기준으로 해석할지(§4 ①) 결정.
12. **캐시 동일성과 시맨틱 재사용 호환성 계약 분리 확정**: fileKey/nodeId/fileVersion은 **캐시 동일성**(§2.4) 전용으로, `sourceType`+스키마버전+매퍼버전+featureType/archetype은 **시맨틱 재사용 호환성**(`findReusableCandidates`, §2.6-b) 전용으로 분리 설계 — fileKey를 재사용 호환성 조건에 넣지 않는다.
13. **기존 DB DDL 유지 시 `PROVIDER_ID`/`MODEL_ID`/`PROMPT_VERSION`(전부 NOT NULL)의 Figma 저장값 계약 확정**: 예) `PROVIDER_ID="figma"`, `MODEL_ID`는 배타 모드면 `"deterministic-mapper"`/하이브리드면 실제 LLM 모델명, `PROMPT_VERSION`은 `"mapper-v1"` 또는 더 일반화된 `analysisContractVersion`으로 컬럼 의미 재정의(§2.4). 계약을 정의하지 못하면 컬럼 일반화 마이그레이션이 대안.
14. **인프라 방식 결정**: Figma REST API 직접 호출(§1 (1))로 할지, MCP 클라이언트 임베드(§1 (2), 카탈로그 지원 여부 선행 검증 필요)로 할지 — 10번 결정에 종속.
15. **provider 모델 설계 결정**: `figma`를 기존 `provider` 값과 배타적으로 둘지, vision LLM과 병행 2단계로 둘지(2.5절) 결정.
16. **`figmaUrl`/`nodeId` 파라미터 충돌 규칙 확정**: 둘 다 있을 때 불일치 시 거부하는 방식(§2.1)으로 할지 등.
17. **보안 계층 신규 설계**: URL을 외부 요청 대상으로 재사용하지 않는 구조(URI 파싱+scheme/host 정확 비교, §2.2)로 설계, 지원할 Figma `file_type` 범위(`file`/`design`만 허용할지) 확정, 인증 모델별 토큰 저장·관리 정책 확정, fileKey 화이트리스트 적용 여부(§2.2, 배포 형태에 따라 필수일 수 있음), 공공기관 배포 시나리오의 망분리·외부 API 허용 여부를 Figma에 대해서도 별도로 확인(기존 10.5절 하드 게이트와 동일 논리 적용).
18. **POC**: 실제 사내 Figma 파일 몇 개로 (a) 레이어명·컴포넌트명 품질이 실사용 가능한 수준인지, (b) 좌표 → `LayoutSpec` 이산값 매핑 규칙이 기존 vision 경로만큼 신뢰할 수 있는지, (c) 대형 파일에서의 응답 크기·rate limit·성능이 실사용 가능한 수준인지(§2.3-1) 함께 검증.

이 문서는 영향 분석 결과일 뿐이며, 위 항목 중 어느 것도 착수가 결정되지 않았다.
