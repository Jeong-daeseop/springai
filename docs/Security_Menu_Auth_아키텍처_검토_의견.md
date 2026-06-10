# Security_Menu_Auth 구현계획서·아키텍처 검토 의견

> 작성일: 2026-06-10
> 대상: `Security_Menu_Auth_구현계획서.md`, `Security_Menu_Auth_아키텍처_검토.md`

---

## 1. 검토 요약

| 항목 | 판정 |
|---|---|
| 핵심 방향 (Tool 추가가 아니라 기존 Tool 관계 안전화) | 동의 |
| SqlPlan 모델 제안 | 적절 |
| Phase 순서 두 문서 불일치 | 보완 필요 |
| SqlPlan 도입 시점 불명확 | 보완 필요 |
| DB 방언 문제를 "명시"에만 머묾 | 보완 필요 |
| WorkflowGuideTool 방식 B 선택 근거 | 보완 필요 |

---

## 2. 잘 된 부분

### 2-1. 핵심 방향

구현계획서의 다음 방향이 옳다.

> 이번 구현의 핵심은 Tool 추가가 아니라, 기존 Tool 간 관계를 안전하고 예측 가능하게 만드는 것이다.

기능이 없는 게 문제가 아니라, 안전성·검증 가능성이 부족한 상태에서 계속 쌓이는 것이 위험하다.

### 2-2. SqlPlan 모델 제안

아키텍처 검토 문서의 `SqlPlan` 모델 제안이 특히 좋다.

```java
record SqlPlan(
    String title,
    List<String> statements,
    List<String> warnings,
    List<String> nextSteps
) {}
```

SQL 본문 / 경고 / 후속 단계를 분리하면 테스트 단위가 명확해지고, Claude가 Tool 결과를 보고 다음 단계를 판단하기도 쉬워진다.
`SecurityTemplateTool`의 `FilePlan`에 대응하는 개념으로 자연스럽다.

---

## 3. 보완이 필요한 부분

### 3-1. Phase 순서 두 문서 불일치

구현계획서와 아키텍처 검토 문서가 같은 작업을 서로 다른 분류 기준으로 나눴다.

**구현계획서 Phase:**
```
Phase 1. 입력 검증
Phase 2. SQL escape
Phase 3. 중복 검증
Phase 4. Auth 안내
Phase 5. WorkflowGuideTool
Phase 6. 테스트
Phase 7. description
```

**아키텍처 검토 점진적 리팩터링 순서:**
```
Phase 1. 시그니처 유지
Phase 2. Spec 도입
Phase 3. Validator 분리
Phase 4. SqlBuilder 분리
Phase 5. ResultBuilder 분리
Phase 6. Repository 분리
Phase 7. Workflow 정의 기반화
```

실제 구현에 들어가면 어느 Phase 순서를 따라야 하는지 혼란이 생긴다.

**권장:** 두 문서의 Phase를 하나로 통일하거나, 명시적으로 매핑 테이블을 작성한다.

---

### 3-2. SqlPlan 도입 시점 불명확

아키텍처 검토에서 `SqlPlan`을 제안했지만, 구현계획서의 어느 Phase에서 도입할지 명시되지 않았다.

`SqlPlan`은 `SqlBuilder`와 `ResultBuilder`의 중간 산출물이다. Spec 도입(Phase 2)과 함께 모델을 정의해두지 않으면 이후 Phase에서 구조가 흔들린다.

**권장:** Spec 도입 Phase에 `SqlPlan` record 정의를 함께 포함한다.

---

### 3-3. DB 방언 문제를 "명시"에만 머묾

구현계획서 8.1에서 "MySQL/MariaDB 기준임을 명시한다"고 처리했는데, 이는 위험을 사용자에게 떠넘기는 방식이다.

eGovFrame 공공 SI 환경에서는 Oracle 비중이 높다. `LIMIT`, `NOW()`, `CAST(... AS UNSIGNED)` 등은 Oracle에서 동작하지 않는다.

**권장 선택지:**

| 선택 | 설명 |
|---|---|
| Oracle 대응 SQL 병기 | MySQL/MariaDB + Oracle 두 버전 출력 |
| DB 파라미터 수신 | `generateMenuInsertSql(..., String dbType)` |
| Phase에 이슈로 명시 | 현 단계에서 해결하지 않더라도 별도 Phase로 추가 |

현 시점에서 해결하지 않더라도, **Phase로 명시적으로 올려두는 것이 최소 요건**이다.

---

### 3-4. WorkflowGuideTool 방식 B 선택 근거 보완 필요

아키텍처 검토에서 방식 B(`suggestSecurityMenuAuthWorkflow` 별도 추가)를 선택했지만, 장기적으로 workflow가 늘어날수록 Tool 메서드가 함께 늘어나는 구조다.

방식 A(`workflowType` 파라미터로 통합)의 단점으로 지적한 "기존 호출자 호환성 깨짐"은, `@Tool` description에 `workflowType="crud"` 기본값을 명시하면 완화할 수 있다.

**권장:** 방식 B로 결정했다면 "방식 A로 전환하는 조건"을 명시한다.

예시:

```text
workflow 종류가 4개 이상이 되면 방식 A(workflowType 파라미터)로 전환을 검토한다.
```

---

## 4. 구현 시작 전 결정 사항

구현 시작 전에 한 가지만 결정하면 된다.

> **`SqlPlan`을 별도 record로 먼저 정의한 뒤 리팩터링을 진행할 것인가,
> 아니면 기존 Service에 검증·escape를 먼저 추가하고 나중에 추출할 것인가?**

| 선택 | 장점 | 단점 |
|---|---|---|
| SqlPlan 먼저 | 구조가 처음부터 명확, 테스트 단위 좋음 | 초기 구현 비용 높음 |
| 검증·escape 먼저 | 안전성 문제를 빠르게 해결 | 나중에 리팩터링 비용 추가 |

아키텍처 검토의 "점진적 리팩터링" 기조를 따른다면 **검증·escape 먼저**가 더 현실적이다.

단, 그 경우 `SqlPlan`을 도입할 Phase를 계획서에 명시해두는 것을 권장한다.

---

## 5. 권장 통합 Phase 순서 (안)

두 문서를 통합한 현실적 Phase 순서 제안이다.

```text
Phase 1. MenuRegistrationSpec / AuthRegistrationSpec 도입 + SqlPlan record 정의
          (입력값 정규화 + 결과 모델 구조 확정)

Phase 2. MenuInputValidator / AuthInputValidator 분리
          (null/blank/숫자/URL 형식 검증)

Phase 3. MenuSqlBuilder / AuthSqlBuilder 분리
          (SQL escape, INSERT SQL 조립, ROLE_PTTRN 생성)

Phase 4. MenuResultBuilder / AuthResultBuilder 분리
          (경고 / securityMapper 안내 / 후속 단계 포함)

Phase 5. MenuService / AuthService 조율자로 축소
          (Repository 조회 + Spec → SqlPlan 흐름 연결)

Phase 6. MenuServiceTest / AuthServiceTest 추가

Phase 7. WorkflowGuideTool suggestSecurityMenuAuthWorkflow() 추가
          + WorkflowGuideService 구조 분리

Phase 8. WorkflowGuideServiceTest 추가

Phase 9. Tool description 업데이트

Phase 10. DB 방언 처리 (MySQL/Oracle 분기 또는 병기)
```
