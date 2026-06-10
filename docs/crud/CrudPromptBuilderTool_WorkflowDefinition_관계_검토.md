# CrudPromptBuilderTool 과 WorkflowDefinition 관계 검토

## 1. 검토 목적

이 문서는 `CrudPromptBuilderTool`, `WorkflowGuideTool`, `WorkflowDefinition`의 역할 차이를 정리하고, `WorkflowDefinition`을 구현할 경우 `CrudPromptBuilderTool`이 어떻게 영향을 받는지 검토하기 위한 문서이다.

핵심 질문은 다음이다.

> `CrudPromptBuilderTool`은 `WorkflowGuideTool` 또는 `WorkflowDefinition`과 같은 것인가?  
> `WorkflowDefinition`을 구현하면 `CrudPromptBuilderTool`도 바뀌어야 하는가?

## 2. 핵심 결론

`CrudPromptBuilderTool`과 `WorkflowGuideTool`은 역할이 다르다.

`WorkflowDefinition`을 구현한다고 해서 `CrudPromptBuilderTool`이 반드시 바로 바뀌어야 하는 것은 아니다.

다만 장기적으로는 `WorkflowGuideTool`의 CRUD 진행 단계와 `CrudPromptBuilderTool`의 CRUD 생성 레이어 정보가 중복될 수 있으므로, 정의 기반으로 연결하는 것이 좋다.

최종 판단은 다음과 같다.

> `WorkflowDefinition`은 진행 안내의 표준 정의다.  
> `CrudPromptBuilderTool`은 CRUD 생성 프롬프트 / 실행 도구다.  
> 둘은 같은 것이 아니지만, CRUD 생성 순서 정보를 공유하므로 장기적으로는 정의 기반으로 연결하는 것이 좋다.

## 3. 현재 역할 차이

### 3.1 WorkflowGuideTool

`WorkflowGuideTool`은 현재까지의 작업 상태를 보고 다음에 무엇을 해야 하는지 안내하는 도구다.

예시는 다음과 같다.

```text
현재 상태:
  VO 생성 완료

WorkflowGuideTool 역할:
  다음은 Mapper 생성입니다.
  Tool: getCodeTemplate("mapper")
```

즉, `WorkflowGuideTool`은 진행 안내자다.

현재 구조는 다음과 같다.

```text
WorkflowGuideTool
  → WorkflowGuideService
    → static WORKFLOW
    → 완료 단계 감지
    → 출력 문자열 생성
```

현재 `WorkflowGuideService`는 CRUD 생성 표준 14단계를 static list로 들고 있다.

주요 단계는 다음과 같다.

```text
1. 스키마 조회
2. VO 생성
3. Mapper 생성
4. MapperXml 생성
5. Service 생성
6. ServiceImpl 생성
7. Controller 생성
8. 목록JSP 생성
9. 상세JSP 생성
10. 등록JSP 생성
11. 수정JSP 생성
12. 소스 검증
13. 생성 이력 저장
14. 완성도 점검
```

### 3.2 CrudPromptBuilderTool

`CrudPromptBuilderTool`은 실제 CRUD 생성에 필요한 프롬프트를 만들거나, auto 모드에서 직접 파일 생성을 수행하는 도구다.

주요 역할은 다음과 같다.

- 테이블 스키마 기반 플레이스홀더 계산
- 공통코드/컬럼 정보 반영
- CRUD 전체 생성 프롬프트 반환
- auto 모드에서 11개 레이어 파일 직접 생성
- 생성된 코드 검증
- 생성 이력 저장

현재 `CrudPromptBuilderTool`의 auto 모드 생성 레이어는 다음과 같다.

```text
vo
mapper
mapperXml
service
serviceImpl
controller
controlleradvice
jspList
jspDetail
jspRegist
jspUpdt
```

즉, `CrudPromptBuilderTool`은 생성 지시자 또는 생성 실행자에 가깝다.

## 4. 두 Tool의 본질적 차이

| 구분 | WorkflowGuideTool | CrudPromptBuilderTool |
|---|---|---|
| 주 역할 | 다음 단계 안내 | CRUD 생성 프롬프트 생성 또는 auto 생성 실행 |
| 입력 | 현재 진행 상황 | database, tableName, domain, packageName, outputPath |
| 출력 | 다음 단계, 남은 단계, 진행률 | 생성 프롬프트 또는 생성 완료 결과 |
| 기준 단위 | 사용자 작업 단계 | 실제 생성 레이어 / 파일 |
| 실행 성격 | 안내 | 생성 지시 또는 생성 실행 |
| 예시 | 다음은 Controller 생성 | Controller.java, ValidationHandler.java 생성 |

정리하면 다음과 같다.

```text
WorkflowGuideTool
  = 무엇을 다음에 해야 하는지 알려주는 도구

CrudPromptBuilderTool
  = CRUD 소스를 어떻게 생성할지 지시하거나 직접 생성하는 도구
```

## 5. WorkflowDefinition이란 무엇인가

`WorkflowDefinition`은 특정 workflow의 단계 정보를 구조화한 모델이다.

예시는 다음과 같다.

```java
WorkflowDefinition(
    workflowType = "crud",
    steps = List.of(
        Step(1, "스키마 조회", "getTableSchema(...)"),
        Step(2, "VO 생성", "getCodeTemplate(\"vo\")"),
        Step(3, "Mapper 생성", "getCodeTemplate(\"mapper\")")
    )
)
```

`WorkflowDefinition`을 도입하면 `WorkflowGuideTool`은 static CRUD 전용 구조에서 벗어날 수 있다.

개선 방향은 다음과 같다.

```text
WorkflowGuideTool
  → WorkflowGuideService
    → WorkflowDefinitionRegistry
    → WorkflowProgressDetector
    → WorkflowGuideRenderer
```

이렇게 되면 여러 workflow를 같은 구조로 안내할 수 있다.

예시는 다음과 같다.

```text
workflowType = "crud"
workflowType = "security-menu-auth"
workflowType = "project-initializr"
```

## 6. WorkflowDefinition 구현 시 CrudPromptBuilderTool 영향

### 6.1 즉시 영향

`WorkflowDefinition`을 구현해도 `CrudPromptBuilderTool`은 즉시 바뀌지 않아도 된다.

이유는 다음과 같다.

- `CrudPromptBuilderTool`은 안내 도구가 아니다.
- `CrudPromptBuilderTool`은 실제 CRUD 생성 프롬프트를 만든다.
- auto 모드에서는 직접 파일을 생성하고 저장한다.
- Workflow 안내와 실제 생성 레이어는 단위가 다르다.

특히 `CrudPromptBuilderTool`의 auto 모드는 다음 작업을 수행한다.

```text
PlaceholderValues 계산
↓
LAYERS 순회
↓
codeService.generateSource()
↓
codeService.saveGeneratedCode()
↓
validateDirectory()
↓
saveHistory()
```

이 흐름은 `WorkflowGuideTool`이 대신하면 안 된다.

### 6.2 장기 영향

장기적으로는 `CrudPromptBuilderTool`도 일부 영향을 받는 것이 바람직하다.

이유는 `WorkflowGuideService`의 CRUD 단계와 `CrudPromptBuilderTool`의 생성 레이어가 같은 도메인 정보를 중복해서 들고 있기 때문이다.

현재 예시는 다음과 같다.

```text
WorkflowGuideService:
  Step 7. Controller 생성

CrudPromptBuilderTool:
  controller
  controlleradvice
```

`CrudPromptBuilderTool`에는 `controlleradvice` 레이어가 있지만, `WorkflowGuideService`의 CRUD workflow에는 별도 단계가 없다.

이런 식으로 두 정의가 따로 관리되면 장기적으로 불일치가 발생할 수 있다.

## 7. 하나로 합치면 안 되는 이유

`WorkflowDefinition`과 `CrudPromptBuilderTool`의 생성 레이어를 완전히 하나로 합치는 것은 권장하지 않는다.

이유는 두 모델의 기준 단위가 다르기 때문이다.

### 7.1 WorkflowDefinition의 단위

`WorkflowDefinition`은 사용자가 이해하기 쉬운 작업 단위다.

예시는 다음과 같다.

```text
Controller 생성
JSP 생성
소스 검증
생성 이력 저장
```

### 7.2 CrudGenerationDefinition의 단위

CRUD 생성 정의는 실제 파일 생성 단위다.

예시는 다음과 같다.

```text
controller
controlleradvice
jspList
jspDetail
jspRegist
jspUpdt
```

즉, Workflow 단계 하나가 여러 생성 레이어와 매핑될 수 있다.

예시는 다음과 같다.

```text
Workflow Step: Controller 생성
  → generation layers:
    - controller
    - controlleradvice

Workflow Step: JSP 생성
  → generation layers:
    - jspList
    - jspDetail
    - jspRegist
    - jspUpdt
```

따라서 둘은 같은 모델이 아니라 관계를 갖는 모델로 보는 것이 맞다.

## 8. 권장 구조

권장 구조는 다음과 같다.

```text
WorkflowGuideTool
  → WorkflowDefinitionRegistry
     - crud workflow
     - security-menu-auth workflow

CrudPromptBuilderTool
  → CrudGenerationDefinition
     - vo
     - mapper
     - mapperXml
     - service
     - serviceImpl
     - controller
     - controlleradvice
     - jspList
     - jspDetail
     - jspRegist
     - jspUpdt
```

둘 사이에는 매핑을 둔다.

```text
CRUD Workflow Step 2: VO 생성
  → generation layer: vo

CRUD Workflow Step 7: Controller 생성
  → generation layers:
    - controller
    - controlleradvice

CRUD Workflow Step 8~11: JSP 생성
  → generation layers:
    - jspList
    - jspDetail
    - jspRegist
    - jspUpdt
```

이렇게 하면 다음 장점이 있다.

- WorkflowGuideTool은 사용자에게 보기 좋은 단계로 안내할 수 있다.
- CrudPromptBuilderTool은 실제 파일 생성 단위로 작업할 수 있다.
- 두 도구 간 순서 정보 불일치를 줄일 수 있다.
- 생성 레이어 추가 시 Workflow 안내도 함께 점검할 수 있다.

## 9. 구현 순서 제안

### Phase 1. WorkflowDefinition 도입

먼저 `WorkflowGuideTool` 쪽에 `WorkflowDefinition`을 도입한다.

대상은 다음이다.

- `WorkflowDefinition`
- `WorkflowStep`
- `WorkflowDefinitionRegistry`
- `WorkflowProgressDetector`
- `WorkflowGuideRenderer`

기존 `suggestNextStep(String currentContext)`는 유지한다.

새 workflow 확장을 위해 다음 중 하나를 선택한다.

```java
suggestNextStep(String workflowType, String currentContext)
```

또는 기존 호환을 위해 다음처럼 별도 메서드를 추가한다.

```java
suggestSecurityMenuAuthWorkflow(String currentContext)
```

### Phase 2. CrudPromptBuilderTool 기존 동작 유지

`CrudPromptBuilderTool`의 기존 메서드는 유지한다.

```java
buildFullCrudPrompt(...)
buildMasterDetailPrompt(...)
buildJoinSelectPrompt(...)
```

auto 모드도 그대로 유지한다.

이 단계에서는 WorkflowDefinition과 CrudPromptBuilderTool을 직접 연결하지 않는다.

### Phase 3. CrudGenerationDefinition 도입

이후 `CrudPromptBuilderTool`의 `LAYERS` 배열을 별도 정의로 분리한다.

예시는 다음과 같다.

```text
CrudGenerationDefinition
  → CrudGenerationLayer 목록
```

각 layer는 다음 정보를 가진다.

```text
layerKey
fileSuffix
targetSubPath
fileNameRule
relatedWorkflowStep
```

### Phase 4. WorkflowDefinition과 CrudGenerationDefinition 매핑

마지막으로 workflow 단계와 실제 생성 레이어의 관계를 명시한다.

예시는 다음과 같다.

```text
WorkflowStep(controller)
  → CrudGenerationLayer(controller)
  → CrudGenerationLayer(controlleradvice)
```

이 단계까지 가면 WorkflowGuideTool과 CrudPromptBuilderTool의 기준 정보가 서로 어긋날 가능성이 줄어든다.

## 10. 구현 시 주의사항

### 10.1 CrudPromptBuilderTool을 WorkflowGuideTool로 대체하면 안 됨

`CrudPromptBuilderTool`은 생성 도구다.

`WorkflowGuideTool`은 안내 도구다.

따라서 WorkflowGuideTool이 CrudPromptBuilderTool의 기능을 대신해서는 안 된다.

### 10.2 Workflow 단계와 생성 레이어는 1:1이 아님

하나의 workflow 단계가 여러 파일 생성 레이어를 포함할 수 있다.

따라서 단순히 `WorkflowStep = Layer`로 모델링하면 안 된다.

### 10.3 기존 Tool 호출 호환성 유지

기존 MCP Tool 메서드 시그니처는 유지하는 것이 안전하다.

특히 다음 메서드는 기존 사용자/AI 호출 흐름을 깨지 않도록 유지한다.

```java
suggestNextStep(String currentContext)
buildFullCrudPrompt(...)
buildMasterDetailPrompt(...)
buildJoinSelectPrompt(...)
```

### 10.4 auto 모드와 claude 모드 모두 고려

`CrudPromptBuilderTool`은 `llmProvider`에 따라 동작이 다르다.

```text
claude 모드:
  프롬프트 반환

auto 모드:
  Tool 내부에서 직접 생성 및 저장
```

따라서 생성 레이어 정의를 분리할 때 두 모드가 모두 같은 정의를 사용할 수 있게 해야 한다.

## 11. 최종 판단

`WorkflowDefinition`을 구현하면 가장 먼저 바뀌어야 하는 대상은 `WorkflowGuideTool`이다.

`CrudPromptBuilderTool`은 즉시 바뀌지 않아도 된다.

다만 장기적으로는 다음 구조가 좋다.

```text
1. WorkflowDefinition 구현
   → WorkflowGuideTool 개선

2. CrudPromptBuilderTool은 기존 동작 유지
   → buildFullCrudPrompt(), auto 모드 유지

3. CrudGenerationDefinition 도입
   → CrudPromptBuilderTool의 LAYERS를 외부 정의로 분리

4. WorkflowDefinition과 CrudGenerationDefinition 매핑
   → 안내 단계와 실제 생성 레이어 불일치 방지
```

최종 결론은 다음이다.

> WorkflowDefinition은 진행 안내의 표준 정의다.  
> CrudPromptBuilderTool은 CRUD 생성 프롬프트 / 실행 도구다.  
> 둘은 같은 것이 아니므로 바로 합치면 안 된다.  
> 다만 CRUD 생성 순서 정보를 공유하므로, 장기적으로는 WorkflowDefinition과 CrudGenerationDefinition을 매핑하는 구조가 가장 안전하다.

