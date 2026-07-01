# ProjectInitializr 후속 Workflow 구현 플랜

## 1. 목적

`ProjectInitializrTool.initializeProject()`는 eGovFrame 신규 프로젝트 골격을 생성한다.
현재 생성 결과에는 `PROJECT_CONTEXT`와 정적 "다음 단계" 안내가 포함되어 있지만,
MCP 클라이언트나 AI 에이전트가 다음 Tool 호출을 일관되게 선택하도록 만드는 workflow 계약은 아직 약하다.

이 문서의 목표는 `initializeProject` 실행 후 다음 흐름을 명시적으로 안내하는 구조를 구현하는 것이다.

```text
initializeProject
→ DB 설정
→ DB 스키마 조회
→ buildFullCrudPrompt
→ saveGeneratedCode
→ 빌드 검증
→ 프로젝트 상태 확인
→ 필요 시 Security/Menu/Auth 적용
```

## 2. 현재 상태

### 2.1 구현되어 있는 것

- `ProjectInitializrTool.initializeProject(...)`
  - WAR/Boot, Maven/Gradle, eGovFrame 4.3/5.0 프로젝트 생성
  - 결과 문자열에 `[PROJECT_CONTEXT]` 블록 포함
- `WorkflowGuideTool.suggestNextStep(String currentContext)`
  - CRUD 생성 14단계 안내
- `WorkflowGuideTool.suggestSecurityMenuAuthWorkflow(String currentContext)`
  - Security/Menu/Auth 9단계 안내
- `WorkflowProgressDetector`
  - currentContext 문자열의 키워드 기반으로 완료 단계를 추정

### 2.2 부족한 점

- `initializeProject` 결과와 `WorkflowGuideTool` 사이의 직접 연결 안내가 약하다.
- CRUD workflow는 `DB 스키마 조회`부터 시작하므로 `프로젝트 초기화 완료` 상태를 workflow 단계로 표현하지 못한다.
- `PROJECT_CONTEXT`의 `projectType`, `buildTool`, `egovVersion`, `rootPath` 값이 workflow 판단에 활용되지 않는다.
- `ResultBuilder`의 Security 안내가 선택 workflow인지 명확하지 않다.
- `initializeProject 결과 → 다음 workflow 제안` 계약을 검증하는 테스트가 없다.

## 3. 구현 범위

### 3.1 1차 구현 범위

1차 구현은 기존 구조를 크게 바꾸지 않고 다음 항목만 추가한다.

- `project-setup-crud` workflow 추가
- `WorkflowGuideTool.suggestProjectSetupCrudWorkflow(...)` 추가
- `WorkflowGuideService.suggestProjectSetupCrudWorkflow(...)` 추가
- `ResultBuilder`의 후속 workflow 안내 보강
- 키워드 기반 감지에 `PROJECT_CONTEXT`, `initializeProject`, `DB 정보 설정` 등 추가
- 단위 테스트 추가

### 3.2 방식 B 유지 결정

`WorkflowGuideTool`에는 현재 다음 운영 기준이 적혀 있다.

```text
방식 B(전용 메서드)로 운영 중. workflow 종류 3개 이상 또는
suggest*Workflow() 메서드 2개 이상 추가 시 방식 A(workflowType 파라미터)로 전환 예정.
```

`suggestProjectSetupCrudWorkflow(...)`를 추가하면 workflow 종류가 3개가 되고,
전용 안내 메서드도 `suggestNextStep`, `suggestSecurityMenuAuthWorkflow`,
`suggestProjectSetupCrudWorkflow`로 늘어난다.
따라서 기존 주석의 방식 A 전환 조건을 충족한다.

다만 1차 구현에서는 방식 A로 전환하지 않는다.
이유는 다음과 같다.

- 기존 MCP Tool 호출 계약을 변경하지 않는다.
- `suggestNextStep(String currentContext)`가 이미 CRUD 기본 workflow 의미로 사용되고 있다.
- project setup workflow 추가 목적은 초기화 직후 흐름 보강이며, workflow API 일반화가 아니다.
- 방식 A 전환은 기존 클라이언트 호출 방식과 문서 전체를 함께 바꿔야 하므로 별도 리팩터링 범위로 둔다.

대신 이번 구현에서 `WorkflowGuideTool`의 주석을 다음 기준으로 갱신한다.

```text
현재는 호환성 때문에 방식 B(전용 메서드)를 유지한다.
신규 workflow 추가가 더 발생하거나 호출자가 workflowType을 안정적으로 전달할 수 있게 되면
suggestNextStep(String workflowType, String currentContext) 방식으로 전환한다.
```

### 3.3 2차 구현 후보

다음 항목은 1차 구현 후 필요성이 확인되면 진행한다.

- `ProjectContextParser` 도입
- `WorkflowProgressDetector`를 구조화 context 기반으로 확장
- `WorkflowGuideTool`을 `suggestNextStep(String workflowType, String currentContext)` 형태로 일반화
- SecurityTemplateTool 정책과 `ResultBuilder` Security 안내 정합성 재검토

## 4. 신규 Workflow 정의

### 4.1 workflow type

```text
project-setup-crud
```

### 4.2 단계

| No | 단계 | Tool/Action | 설명 | 감지 키워드 |
|----|------|-------------|------|-------------|
| 1 | 프로젝트 초기화 | initializeProject | eGovFrame 프로젝트 골격 생성 | `PROJECT_CONTEXT`, `initializeProject`, `프로젝트 초기화`, `초기화 완료` |
| 2 | DB 설정 | 수동 설정 | `context-datasource.xml` 또는 `application.yml` DB 접속 정보 설정 | `DB 정보 설정`, `context-datasource.xml`, `application.yml`, `datasource` |
| 3 | DB 스키마 조회 | getTableSchema | 대상 테이블 컬럼/PK/타입 확인 | `getTableSchema`, `스키마`, `테이블`, `schema` |
| 4 | CRUD 프롬프트 생성 | buildFullCrudPrompt | eGovVersion/projectType에 맞는 CRUD 생성 프롬프트 작성 | `buildFullCrudPrompt`, `CRUD 프롬프트`, `프롬프트 생성` |
| 5 | CRUD 코드 저장 | saveGeneratedCode | VO/Mapper/Service/Controller/View 파일 저장 | `saveGeneratedCode`, `코드 저장`, `VO`, `Mapper`, `Controller` |
| 6 | 생성 이력 저장 | saveGenerationHistory | 생성 파일 및 작업 이력 기록 | `saveGenerationHistory`, `이력`, `history` |
| 7 | 빌드 검증 | Maven/Gradle | 생성 프로젝트 컴파일 및 패키징 확인 | `mvn clean package`, `./gradlew build`, `bootRun`, `빌드` |
| 8 | 프로젝트 상태 확인 | checkProjectHealth | 최종 구조와 설정 상태 점검 | `checkProjectHealth`, `health`, `상태 확인` |
| 9 | Security/Menu/Auth 선택 | suggestSecurityMenuAuthWorkflow | 보안, 메뉴, 권한 등록이 필요한 경우 후속 workflow 실행 | `Security`, `Menu`, `Auth`, `권한`, `메뉴` |

### 4.3 step 1 자기완료 감지 정책

`project-setup-crud` workflow는 `initializeProject` 완료 후 호출하는 것을 주 사용 시나리오로 둔다.
따라서 `currentContext`에 `[PROJECT_CONTEXT]`가 포함되면 1단계 `프로젝트 초기화`는 완료된 것으로 감지한다.

의도한 동작은 다음과 같다.

| 입력 context | 기대 안내 |
|--------------|-----------|
| 빈 문자열 | 전체 9단계 workflow 안내 |
| `[PROJECT_CONTEXT] ... [/PROJECT_CONTEXT]` 포함 | 1단계 완료로 보고 2단계 `DB 설정`을 다음 단계로 안내 |
| `PROJECT_CONTEXT` + `DB 정보 설정 완료` | 2단계 완료로 보고 3단계 `DB 스키마 조회` 또는 CRUD 프롬프트 생성 전 준비를 안내 |

이 정책은 테스트로 고정한다.

## 5. 코드 변경 계획

### 5.1 `WorkflowDefinitionRegistry`

대상 파일:

```text
src/main/java/com/krdevops/springai/service/workflow/WorkflowDefinitionRegistry.java
```

변경 사항:

- 생성자에서 `buildProjectSetupCrudWorkflow()` 등록
- private 메서드 추가

예상 구조:

```java
private WorkflowDefinition buildProjectSetupCrudWorkflow() {
    return new WorkflowDefinition("project-setup-crud", "프로젝트 초기화 후 CRUD 생성 워크플로우", List.of(
        new WorkflowStep(1, "프로젝트 초기화", "initializeProject", "eGovFrame 프로젝트 골격 생성",
            new String[]{"PROJECT_CONTEXT", "initializeProject", "프로젝트 초기화", "초기화 완료"}),
        new WorkflowStep(2, "DB 설정", "수동 설정", "DB 접속 정보 설정",
            new String[]{"DB 정보 설정", "context-datasource.xml", "application.yml", "datasource"}),
        new WorkflowStep(3, "DB 스키마 조회", "getTableSchema", "테이블 구조 파악",
            new String[]{"getTableSchema", "스키마", "테이블", "schema"}),
        new WorkflowStep(4, "CRUD 프롬프트 생성", "buildFullCrudPrompt", "CRUD 생성 프롬프트 작성",
            new String[]{"buildFullCrudPrompt", "CRUD 프롬프트", "프롬프트 생성"}),
        new WorkflowStep(5, "CRUD 코드 저장", "saveGeneratedCode", "CRUD 파일 저장",
            new String[]{"saveGeneratedCode", "코드 저장", "VO", "Mapper", "Controller"}),
        new WorkflowStep(6, "생성 이력 저장", "saveGenerationHistory", "이력 기록",
            new String[]{"saveGenerationHistory", "이력", "history"}),
        new WorkflowStep(7, "빌드 검증", "빌드", "컴파일 및 패키징 확인",
            new String[]{"mvn clean package", "./gradlew build", "bootRun", "빌드"}),
        new WorkflowStep(8, "프로젝트 상태 확인", "checkProjectHealth", "최종 상태 확인",
            new String[]{"checkProjectHealth", "health", "상태 확인"}),
        new WorkflowStep(9, "Security/Menu/Auth 선택", "suggestSecurityMenuAuthWorkflow", "선택 보안 workflow 안내",
            new String[]{"Security", "Menu", "Auth", "권한", "메뉴"})
    ));
}
```

### 5.2 `WorkflowGuideService`

대상 파일:

```text
src/main/java/com/krdevops/springai/service/WorkflowGuideService.java
```

변경 사항:

- 신규 메서드 추가

```java
public String suggestProjectSetupCrudWorkflow(String currentContext) {
    WorkflowDefinition definition = registry.find("project-setup-crud")
            .orElseThrow(() -> new IllegalStateException("project-setup-crud workflow not found"));
    int completedStep = progressDetector.detectCompletedStep(definition, currentContext);
    return guideRenderer.render(definition, completedStep);
}
```

설계 메모:

- 현재 `WorkflowGuideService`는 `WorkflowProgressDetector`와 `WorkflowGuideRenderer`를 직접 생성한다.
- 1차 구현에서는 기존 패턴을 유지한다.
- 다만 `WorkflowProgressDetector`가 `PROJECT_CONTEXT` 파싱 등으로 복잡해지면 생성자 주입으로 전환한다.
- 그 시점에는 `WorkflowGuideServiceTest`에서 detector를 목 또는 테스트 더블로 대체할 수 있게 한다.

### 5.3 `WorkflowGuideTool`

대상 파일:

```text
src/main/java/com/krdevops/springai/tools/WorkflowGuideTool.java
```

변경 사항:

- MCP Tool 메서드 추가
- 방식 A 전환 조건 주석 갱신

```java
@Tool(description = """
        ProjectInitializrTool.initializeProject() 실행 후 다음 작업을 안내하는 workflow 도구.
        initializeProject 결과의 PROJECT_CONTEXT 블록과 현재 완료한 작업 내용을 currentContext에 넣으면
        DB 설정, DB 스키마 조회, CRUD 프롬프트 생성, 코드 저장, 빌드 검증 순서로 다음 단계를 안내합니다.
        """)
public String suggestProjectSetupCrudWorkflow(String currentContext) {
    return workflowGuideService.suggestProjectSetupCrudWorkflow(currentContext);
}
```

주석 갱신 방향:

```text
※ 현재는 기존 호출자 호환성을 위해 방식 B(전용 메서드)를 유지한다.
   workflowType 기반 방식 A 전환은 별도 리팩터링으로 진행한다.
```

### 5.4 `ResultBuilder`

대상 파일:

```text
src/main/java/com/krdevops/springai/service/initializr/ResultBuilder.java
```

변경 사항:

- `📋 다음 단계` 문구를 `projectType`, `buildTool` 기준으로 더 명확히 작성
- `suggestProjectSetupCrudWorkflow`는 독립 실행 단계가 아니라 후속 workflow 확인용 참조로 낮춘다.
- Security는 선택 workflow로 분리해서 표현
- 기존 `s.cap().spring6()` 기반 Security `if/else` 분기는 제거하고, `suggestSecurityMenuAuthWorkflow(PROJECT_CONTEXT 블록)` 단일 참조로 대체한다.

예상 문구:

```text
📋 다음 단계
  1. context-datasource.xml DB 정보 설정
  2. buildFullCrudPrompt(..., egovVersion="5.0", viewType="jsp") 로 CRUD 소스 생성
     - viewType: "jsp" 또는 "thymeleaf" 선택 가능 (projectType 파라미터 없음)
     - buildFullCrudPrompt는 내부에서 getTableSchema와 공통코드 조회를 함께 처리합니다.
  3. saveGeneratedCode 또는 auto orchestration 결과에 따라 파일 저장 확인
  4. mvn clean package 로 빌드 검증

후속 workflow를 단계별로 확인하려면
  → suggestProjectSetupCrudWorkflow(PROJECT_CONTEXT 블록 + "프로젝트 초기화 완료")

선택: Security/Menu/Auth 적용이 필요하면
  → suggestSecurityMenuAuthWorkflow(PROJECT_CONTEXT 블록)
```

주의:

- `war + 5.0`에서 `getSecurityTemplate("javaconfig")`를 즉시 안내하는 현재 방식은 재검토한다.
- Security 적용 안내는 `ResultBuilder`에서 상세 template type을 직접 고정하기보다 `suggestSecurityMenuAuthWorkflow`로 넘기는 편이 안전하다.

## 6. 테스트 계획

### 6.1 `WorkflowGuideServiceTest`

대상 파일:

```text
src/test/java/com/krdevops/springai/service/WorkflowGuideServiceTest.java
```

추가 테스트:

```text
suggestProjectSetupCrudWorkflow_빈context_전체9단계_반환
suggestProjectSetupCrudWorkflow_PROJECT_CONTEXT_초기화완료_다음단계_DB설정
suggestProjectSetupCrudWorkflow_DB설정완료_다음단계_DB스키마조회
suggestProjectSetupCrudWorkflow_buildFullCrudPrompt완료_다음단계_CRUD코드저장
suggestProjectSetupCrudWorkflow_빌드완료_다음단계_상태확인
```

검증 포인트:

- 신규 workflow title 포함
- 1단계부터 9단계까지 렌더링
- 빈 context는 전체 안내를 반환
- `PROJECT_CONTEXT` 포함 context는 1단계 자기완료로 감지해 `DB 설정`을 다음 단계로 표시
- 완료 context에 따라 `← 다음 단계` 표시 위치가 맞는지 확인

### 6.2 `ResultBuilder` 테스트

새 테스트 파일 후보:

```text
src/test/java/com/krdevops/springai/service/initializr/ResultBuilderTest.java
```

추가 테스트:

```text
build_warMaven50_includesProjectContextAndProjectSetupWorkflowHint
build_warMaven50_includesMavenPackageCommand
build_bootGradle50_includesBootRunCommand
build_securityGuide_isOptionalWorkflow
```

검증 포인트:

- `[PROJECT_CONTEXT]` 포함
- `suggestProjectSetupCrudWorkflow` 포함
- `suggestSecurityMenuAuthWorkflow` 포함
- WAR Maven은 `mvn clean package`
- Boot Gradle은 `./gradlew bootRun`
- Security가 필수 단계처럼 표현되지 않는지 확인

테스트 객체 구성:

- `ProjectSpec`은 `ProjectSpec.of(projectName, groupId, artifactId, packageName, buildTool, projectType, outputPath, cap)` 팩토리로 생성한다.
- `cap`은 기존 테스트처럼 `VersionCapabilityResolver.resolve("5.0")` 또는 `resolve("4.3")`로 만든다.
- `GenerationReport`는 `new GenerationReport(spec.root().toString())`로 만들고, 파일 목록 검증이 필요하면 `FilePlan`을 통해 `added(...)`를 호출한다.

## 7. 문서 갱신 계획

다음 문서를 갱신한다.

```text
docs/project-initializr/ProjectInitializr_가이드.md
docs/project-initializr/project_initializr.md
docs/misc/mcp_tools.md
```

추가할 내용:

```text
initializeProject 이후에는 suggestProjectSetupCrudWorkflow를 호출해
DB 설정, DB 스키마 조회, CRUD 생성, 빌드 검증, 선택 Security 적용 순서를 안내받는다.
```

## 8. 검증 명령

1차 구현 후 다음 명령을 실행한다.

```bash
./gradlew test --tests "com.krdevops.springai.service.WorkflowGuideServiceTest"
./gradlew test --tests "com.krdevops.springai.service.initializr.*"
./gradlew test
```

## 9. 구현 우선순위

### P1

- `project-setup-crud` workflow 추가
- `suggestProjectSetupCrudWorkflow` 서비스/Tool 메서드 추가
- `ResultBuilder` 후속 workflow 안내 추가
- `WorkflowGuideServiceTest` 추가

### P2

- `ResultBuilderTest` 추가
- Security 안내 문구 정합성 보완
- 관련 문서 갱신

### P3

- `ProjectContextParser` 도입
- workflow type 일반화
- 구조화 context 기반 progress detection

## 10. 기대 효과

- `ProjectInitializrTool`이 단순 파일 생성 도구에서 신규 프로젝트 생성 workflow의 진입점으로 확장된다.
- AI/MCP 클라이언트가 생성 직후 다음 Tool을 안정적으로 선택할 수 있다.
- `egovVersion`, `projectType`, `buildTool` 전달 누락 가능성이 줄어든다.
- CRUD 생성, 빌드 검증, Security/Menu/Auth 적용 흐름을 분리하면서도 자연스럽게 연결할 수 있다.
