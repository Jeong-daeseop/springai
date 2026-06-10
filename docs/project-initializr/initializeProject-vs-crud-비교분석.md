# initializeProject vs CRUD 생성 — 구현 방식 심층 비교분석

> 작성일: 2026-06-08
> 대상: springai-mcp 프로젝트

---

## 1. 구현 주체 요약

| 구분 | `initializeProject` | CRUD (`claude`) | CRUD (`auto`) |
|------|-------------------|----------------|--------------|
| 소스 생성 주체 | **Java 하드코딩** | **Claude LLM** | **Java 템플릿 치환** |
| AI 개입 | 없음 | 있음 (핵심) | 없음 |
| 결정성 | 완전 결정적 | 비결정적 | 완전 결정적 |
| 토큰 소비 | 없음 | 매우 많음 | 거의 없음 |

---

## 2. `initializeProject` — 구현 방식

### 핵심 메커니즘: Java Text Block 하드코딩

```
사용자 파라미터 입력
    ↓
ProjectInitializrTool (위임만)
    ↓
ProjectInitializrService.initializeProject()
    ├── createDirectories()     Files.createDirectories() 직접 호출
    ├── createBuildFile()       bootBuildGradle() / warPomXml() Text Block 반환
    ├── createBootFiles()       application.yml / main클래스 / test클래스
    │   또는 createWarFiles()   context-*.xml / web.xml / dispatcher-servlet.xml
    └── writeFile()             Files.writeString() 직접 디스크 기록
```

### 버전 분기 전략: Capability Matrix 패턴

```java
// 독립 메서드로 버전 특성을 캡슐화
supportsJakarta(v)           // 5.0+ → jakarta.* 패키지
supportsSpring6(v)           // 5.0+ → Spring 6.x
supportsBoot3(v)             // 5.0+ → Spring Boot 3.x
supportsHyphenArtifactId(v)  // 5.0+ → artifactId 명명 규칙 변경
supportsEgovParent(v)        // 5.0+ → eGovFrame Parent POM 사용
```

각 메서드가 독립적이므로 **eGovFrame 5.1 출시 시 해당 메서드만 수정** — 다른 분기에 영향 없음

### 파일 내용 생성 방식

```java
// 완성된 파일 내용을 Java Text Block으로 하드코딩
private String bootBuildGradle(Spec s) {
    return """
        plugins {
            id 'org.springframework.boot' version '%s'
            ...
        }
        """.formatted(sbVer, s.groupId, javaVer, ...);
}
```

- 파라미터를 `String.formatted()`로 주입
- DB 조회 없음, LLM 호출 없음
- **완전히 예측 가능한 출력**

---

## 3. CRUD 생성 — 구현 방식

### 핵심 메커니즘: 2가지 모드 분기

```java
if ("auto".equals(provider)) {
    return orchestrateAuto(...);                          // Java 템플릿 치환
}
return crudPromptBuilderService.buildFullCrudPrompt(...); // Claude LLM
```

---

### 3-1. `claude` 모드 — LLM 주도 생성

```
① DB 스키마 조회 (INFORMATION_SCHEMA)
    ↓
② 플레이스홀더 값 계산 (Java)
   - PK 탐지, camelCase 변환, Java 타입 매핑
   - JSP 테이블 헤더/행, VO 필드, SQL 컬럼 목록 생성
    ↓
③ 공통코드 컬럼 탐지 (_CODE, _CD로 끝나는 컬럼 → COMTCCMMNCODE 매칭)
    ↓
④ 통합 프롬프트 문자열 반환 → Claude에게 전달
    ↓
⑤ Claude가 프롬프트를 읽고 판단하여 11개 파일 순서대로 생성
   generateSource("vo", values)         → saveGeneratedCode(path, code)
   generateSource("mapper", values)     → saveGeneratedCode(...)
   generateSource("mapperXml", values)  → saveGeneratedCode(...)
   ... (11회 반복)
```

**Claude가 실제 소스를 작성** — 공통코드 선택, 비즈니스 로직 판단, 예외 처리 방식 등을 LLM이 결정

#### 생성 파일 목록 (11개)

| Step | layerKey | 파일명 |
|------|----------|--------|
| 1 | `vo` | `{Domain}VO.java` |
| 2 | `mapper` | `{Domain}Mapper.java` |
| 3 | `mapperXml` | `{Domain}Mapper.xml` |
| 4 | `service` | `{Domain}Service.java` |
| 5 | `serviceImpl` | `Egov{Domain}ServiceImpl.java` |
| 6 | `controller` | `Egov{Domain}Controller.java` |
| 7 | `controlleradvice` | `Egov{Domain}ValidationHandler.java` |
| 8 | `jspList` | `Egov{Domain}List.jsp` |
| 9 | `jspDetail` | `Egov{Domain}Detail.jsp` |
| 10 | `jspRegist` | `Egov{Domain}Regist.jsp` |
| 11 | `jspUpdt` | `Egov{Domain}Updt.jsp` |

---

### 3-2. `auto` 모드 — Java 템플릿 주도 생성

```
① buildPlaceholderValues() — DB 스키마 조회 + 값 계산 (claude 모드와 동일)
    ↓
② 11개 레이어 루프 (LLM 호출 없음)
   for (String[] layer : LAYERS) {
       String code = codeService.generateSource(layerKey, values); // 템플릿 치환
       codeService.saveGeneratedCode(filePath, code);               // 파일 저장
   }
    ↓
③ codeValidatorService.validateDirectory() — 생성 코드 일괄 검증
    ↓
④ generationHistoryService.saveHistory()   — 생성 이력 DB 저장
```

`CodeService.generateSource()` 내부에서 레이어별 **고정 템플릿**에 플레이스홀더를 치환

---

## 4. 핵심 차이점 심층 분석

### 4-1. DB 의존성

| | `initializeProject` | CRUD `claude` | CRUD `auto` |
|--|---|---|---|
| DB 조회 | **없음** | `INFORMATION_SCHEMA` 조회 | `INFORMATION_SCHEMA` 조회 |
| 공통코드 조회 | 없음 | `COMTCCMMNCODE` 조회 | `COMTCCMMNCODE` 조회 |
| 생성이력 저장 | 없음 | Claude가 선택적 호출 | **자동 저장** |

> `initializeProject`는 DB 완전 독립 — **MySQL 없이도 실행 가능**

---

### 4-2. 소스 품질과 유연성

| 항목 | `initializeProject` | CRUD `claude` | CRUD `auto` |
|------|---|---|---|
| 출력 일관성 | 항상 동일 | 실행마다 다를 수 있음 | 항상 동일 |
| 비즈니스 로직 반영 | 불가 | **가능** (LLM 판단) | 템플릿 범위 내 |
| 공통코드 SELECT BOX | 불가 | **자동 반영** | 제한적 |
| 커스텀 요구사항 반영 | 불가 | **가능** | 불가 |
| 오타/오류 가능성 | 없음 | 있음 (LLM hallucination) | 없음 |

---

### 4-3. 실행 비용

```
initializeProject
  DB 쿼리       = 0회
  LLM API 호출  = 0회
  토큰 소비     = 0
  실행 시간     = 수십 ms

CRUD claude 모드
  DB 쿼리       = 2~3회 (스키마 + 공통코드)
  LLM API 호출  = 11회 (레이어당 1회)
  토큰 소비     = 수만 토큰
  실행 시간     = 수십 초 ~ 수분

CRUD auto 모드
  DB 쿼리       = 2~3회 (스키마 + 공통코드)
  LLM API 호출  = 0회
  토큰 소비     = ~0 (Tool 호출 오버헤드만)
  실행 시간     = 수백 ms
```

---

### 4-4. 플레이스홀더 처리 방식

#### `initializeProject` — 직접 값 주입

```java
// String.formatted() 로 직접 치환
"""
group = '%s'
""".formatted(s.groupId)
```

#### CRUD `auto` 모드 — Map 기반 치환

```java
// PlaceholderValues.toMap() → CodeService.generateSource()
Map<String, String> values = pv.toMap();
// {PACKAGE}, {DOMAIN}, {PK_FIELD} 등 21개 플레이스홀더 일괄 치환
String code = codeService.generateSource(layerKey, values);
```

#### CRUD `claude` 모드 — LLM 프롬프트 주입

```
{{PACKAGE}}    = egovframework.let.emp
{{DOMAIN}}     = Employer
{{PK_FIELD}}   = emplyrId
...
→ Claude가 위 값을 참조하여 소스 직접 작성
```

---

## 5. 설계 철학 비교

| | `initializeProject` | CRUD 생성 |
|--|--|--|
| 철학 | **"골격은 항상 동일하다"** — 버전 조합별 완성된 템플릿 제공 | **"컬럼이 다르면 소스가 달라진다"** — DB 스키마 기반 동적 생성 |
| 확장 방법 | 새 버전 → Capability Matrix 메서드 추가 | 새 레이어 → LAYERS 배열 + 템플릿 추가 |
| 한계 | 버전 조합만큼 분기 증가 | `auto` 모드는 복잡한 비즈니스 로직 반영 불가 |
| 적합한 상황 | 표준 프로젝트 초기 구조 생성 | 반복적인 CRUD 코드 생성 |

---

## 6. 전체 흐름도

```
┌─────────────────────────────────────────────────────────────────┐
│                        Claude Desktop                           │
│                   (MCP Tool 호출 클라이언트)                      │
└────────────────────────┬────────────────────────────────────────┘
                         │
          ┌──────────────┴───────────────┐
          │                              │
          ▼                              ▼
┌─────────────────────┐      ┌──────────────────────────────┐
│  initializeProject  │      │      buildFullCrudPrompt     │
│  (ProjectInitializr │      │      (CrudPromptBuilderTool) │
│       Tool)         │      └──────────────┬───────────────┘
└─────────┬───────────┘                     │
          │                    ┌────────────┴────────────┐
          ▼                    ▼                         ▼
┌─────────────────┐  ┌──────────────────┐   ┌─────────────────────┐
│  Java Text Block │  │  claude 모드      │   │  auto 모드           │
│  하드코딩 템플릿  │  │  프롬프트 반환   │   │  orchestrateAuto()  │
│                 │  └────────┬─────────┘   └──────────┬──────────┘
│  버전별 분기    │           │                         │
│  Capability     │           ▼                         ▼
│  Matrix 패턴    │  ┌──────────────────┐   ┌─────────────────────┐
└─────────┬───────┘  │  Claude LLM      │   │  CodeService        │
          │          │  (소스 직접 작성) │   │  generateSource()   │
          ▼          └────────┬─────────┘   │  (템플릿 치환)       │
┌─────────────────┐           │             └──────────┬──────────┘
│  Files.write    │           ▼                        ▼
│  (디스크 저장)  │  ┌──────────────────┐   ┌─────────────────────┐
└─────────────────┘  │  saveGenerated   │   │  Files.write        │
                     │  Code()          │   │  + validateDir()    │
                     └──────────────────┘   │  + saveHistory()    │
                                            └─────────────────────┘
```

---

## 7. 결론

```
initializeProject
└── "정해진 틀을 그대로 파일로 쓴다"
    Java가 100% 담당, AI 없음, 빠르고 일관됨

CRUD claude 모드
└── "DB를 분석해 AI에게 설계를 맡긴다"
    Java는 분석·프롬프트 준비만, Claude가 실제 코드 작성

CRUD auto 모드
└── "DB를 분석해 템플릿에 값을 채운다"
    Java가 100% 담당, AI 없음, 빠르고 일관됨
    → initializeProject와 동일한 철학, DB 조회만 추가
```

### 언제 무엇을 쓸까?

| 상황 | 권장 방식 |
|------|----------|
| 새 프로젝트 시작 | `initializeProject` |
| 표준 CRUD, 빠른 생성 필요 | CRUD `auto` 모드 |
| 공통코드 SELECT BOX, 복잡한 비즈니스 로직 반영 필요 | CRUD `claude` 모드 |
| 토큰 절약이 중요한 반복 작업 | CRUD `auto` 모드 |
