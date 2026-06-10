# SecurityTemplateTool / MenuTool / AuthTool 구현 및 적용 순서

## 1. 결론

`SecurityTemplateTool`, `MenuTool`, `AuthTool`은 서로 역할이 다르며, 실제 적용 순서는 다음이 가장 안전하다.

```text
1. SecurityTemplateTool
   ↓
2. securityMapper 포함 여부 확인
   ↓
3. MenuTool - 프로그램/메뉴 SQL 생성
   ↓
4. AuthTool - URL 권한 SQL 생성
   ↓
5. SQL 실행
   ↓
6. 서버 재기동 또는 Security 캐시 갱신
   ↓
7. 메뉴 노출 + URL 접근 권한 테스트
```

핵심 기준은 다음과 같다.

> Security는 기반 인프라, Menu는 화면 노출, Auth는 접근 제어다.

따라서 `MenuTool`, `AuthTool` 구현 자체는 먼저 만들어둘 수 있지만, 실제 프로젝트에 적용하는 순서는 `SecurityTemplateTool`이 먼저 오는 것이 맞다.

## 2. 각 Tool의 역할

| Tool | 주요 역할 | 관련 산출물 |
|---|---|---|
| `SecurityTemplateTool` | eGovFrame Security 기반 파일 생성 | `web.xml.fragment`, `context-security.xml`, 필터, 로그인 페이지, `securityMapper` 등 |
| `MenuTool` | 프로그램/메뉴 DB 등록 SQL 생성 | `COMTNPROGRMLIST`, `COMTNMENUINFO` |
| `AuthTool` | URL 접근 권한 DB 등록 SQL 생성 | `COMTNROLEINFO`, `COMTNAUTHORROLERELATE` |

## 3. 권장 구현 및 적용 순서

### 3.1 SecurityTemplateTool 먼저 적용

먼저 Security 기반을 생성해야 한다.

주요 생성 대상은 다음과 같다.

- `web.xml.fragment`
- `context-security.xml`
- 로그인/로그아웃/로그인정책 필터
- 세션 매핑 클래스
- 로그인 JSP
- UserDetails 관련 파일
- `securityMapper`

이 단계가 먼저 필요한 이유는 명확하다.

메뉴와 권한 DB 데이터가 등록되어 있어도 Spring Security 설정이 없으면 URL 접근 제어가 실제로 동작하지 않는다. 즉, `MenuTool`과 `AuthTool`은 Security 기반 위에서 의미가 생긴다.

### 3.2 securityMapper 포함 여부 확인

권한 DB를 등록하기 전에 `securityMapper`가 생성되어 있는지 확인해야 한다.

`securityMapper`는 보통 다음 테이블의 정보를 Security에서 조회하는 역할을 한다.

- `COMTNROLEINFO`
- `COMTNAUTHORROLERELATE`
- `COMTNEMPLYRSCRTYESTBS`
- 사용자/권한 관계 테이블

따라서 `AuthTool`이 생성한 SQL을 실행해도, Security 쪽 mapper가 해당 데이터를 읽지 못하면 접근 제어에 반영되지 않는다.

### 3.3 MenuTool로 메뉴/프로그램 등록

다음은 메뉴와 프로그램 등록이다.

권장 흐름은 다음과 같다.

```text
1. MenuTool.getMenuStructure(menuNo)
   → 상위 메뉴 위치와 신규 MENU_NO / MENU_ORDR 권장값 확인

2. AuthTool.getProgramList(keyword)
   → PROGRM_FILE_NM 중복 여부 확인

3. MenuTool.generateMenuInsertSql(...)
   → COMTNPROGRMLIST + COMTNMENUINFO INSERT SQL 생성
```

이 단계의 목적은 화면 메뉴에 신규 기능이 보이도록 하는 것이다.

주의할 점은 메뉴 등록과 권한 등록은 같은 것이 아니라는 점이다.

- 메뉴 등록: 사용자가 화면에서 메뉴를 볼 수 있게 함
- 권한 등록: 사용자가 URL에 접근할 수 있게 하거나 차단함

따라서 메뉴가 보여도 권한이 없으면 접근이 차단될 수 있고, 반대로 권한이 있어도 메뉴가 없으면 화면 메뉴에는 보이지 않을 수 있다.

### 3.4 AuthTool로 URL 권한 등록

메뉴/프로그램 등록 후 URL 접근 권한을 등록한다.

권장 흐름은 다음과 같다.

```text
AuthTool.generateAuthInsertSql(
  urlPrefix = "/emp/employer",
  programNm = "직원관리",
  domain = "emp"
)
```

생성되는 주요 SQL은 다음과 같다.

- `COMTNROLEINFO` INSERT
  - URL 패턴 기반 Role 등록
  - 예: `\A/emp/employer/.*\.do.*\Z`

- `COMTNAUTHORROLERELATE` INSERT
  - `ROLE_ADMIN` 등 권한 그룹과 Role 연결

이 단계의 목적은 URL 접근 제어를 Security DB 데이터로 등록하는 것이다.

### 3.5 SQL 실행 후 재기동 또는 캐시 갱신

SQL 실행만으로 권한이 즉시 반영되지 않을 수 있다.

eGovFrame Security는 URL 권한 정보를 기동 시점에 읽거나 내부 캐시에 올려서 사용할 가능성이 있기 때문이다.

따라서 SQL 실행 후 다음 중 하나가 필요하다.

- 애플리케이션 서버 재기동
- Spring Security 권한 캐시 갱신
- Security metadata source 재로딩

현재 Tool 설명에도 이 지점은 명시되어 있으며, 실제 운영 적용 시 반드시 확인해야 한다.

### 3.6 최종 접근 테스트

마지막으로 메뉴 노출과 URL 접근 제어를 분리해서 검증해야 한다.

테스트 항목은 다음과 같다.

| 테스트 | 기대 결과 |
|---|---|
| 관리자 계정으로 메뉴 확인 | 신규 메뉴가 노출됨 |
| 관리자 계정으로 URL 접근 | 정상 접근 가능 |
| 일반 사용자로 URL 접근 | 권한 설정에 따라 허용 또는 차단 |
| 비로그인 상태로 URL 접근 | 로그인 페이지로 이동 |
| 메뉴는 보이나 URL 접근 불가 여부 | 권한 등록 누락 가능성 확인 |
| URL 접근은 되나 메뉴 미노출 여부 | 메뉴 등록 누락 가능성 확인 |

## 4. 구현 관점의 권장 순서

코드 구현 관점에서도 다음 순서가 자연스럽다.

```text
1. SecurityTemplateTool 구현/검증
2. SecurityTemplateTool 조합 타입 완결성 검증
3. securityMapper 생성 검증
4. MenuTool 구현/검증
5. AuthTool 구현/검증
6. MenuTool + AuthTool + SecurityTemplateTool 통합 시나리오 검증
```

특히 통합 시나리오에서는 다음 관계를 검증해야 한다.

```text
MenuTool.generateMenuInsertSql()
  → COMTNPROGRMLIST.URL
  → 실제 Controller URL
  → AuthTool.generateAuthInsertSql().ROLE_PTTRN
  → securityMapper 조회 결과
  → Spring Security 접근 제어
```

## 5. 핵심 판단

최종 판단은 다음과 같다.

`SecurityTemplateTool`은 기반 설정을 만든다.  
`MenuTool`은 기능을 화면 메뉴에 연결한다.  
`AuthTool`은 해당 기능의 URL 접근 권한을 DB에 연결한다.

따라서 실제 적용 순서는 다음 원칙을 따라야 한다.

> 먼저 Security가 URL 권한 정보를 읽을 수 있는 구조를 만든다.  
> 그 다음 메뉴를 등록한다.  
> 마지막으로 URL 권한을 등록하고 재기동 또는 캐시 갱신 후 검증한다.

## 6. 앞으로의 구현 방향

현재 구조가 `SecurityTemplateTool`, `MenuTool`, `AuthTool` 3개로 나뉜 것은 방향이 맞다.

앞으로도 하나의 Tool이 모든 것을 처리하는 방식보다는, 각 Tool이 자기 책임을 명확히 갖고 워크플로우로 연결되는 방향이 바람직하다.

핵심 판단은 다음과 같다.

> 파일 생성 Tool과 DB 등록 SQL 생성 Tool은 분리한다.  
> 대신 사용자는 하나의 적용 흐름으로 따라갈 수 있게 만든다.

현재 3개 Tool의 실패 지점은 서로 다르다.

| Tool | 실패 시 영향 |
|---|---|
| `SecurityTemplateTool` | 애플리케이션 보안 구조 자체가 기동되지 않거나 URL 접근 제어가 동작하지 않음 |
| `MenuTool` | 메뉴가 보이지 않거나 프로그램 중복 등록 오류가 발생함 |
| `AuthTool` | 메뉴는 보이지만 접근이 차단되거나, 반대로 접근 제어가 누락됨 |

따라서 앞으로도 3개 Tool의 단일 책임은 유지해야 한다.

```text
SecurityTemplateTool = 보안 기반 생성
MenuTool             = 메뉴/프로그램 등록 SQL 생성
AuthTool             = URL 권한 등록 SQL 생성
```

## 7. Tool별 발전 방향

### 7.1 SecurityTemplateTool

`SecurityTemplateTool`은 앞으로도 메뉴나 권한 SQL까지 직접 만들면 안 된다.

이 Tool의 책임은 Security가 DB 권한 정보를 읽고 URL 접근 제어를 수행할 수 있는 기반을 완결하는 것이다.

강화 방향은 다음과 같다.

- 생성 조합이 참조하는 파일과 클래스를 모두 포함하는지 검증한다.
- `securityMapper` 포함 여부를 조합 이름과 설명에서 명확히 표현한다.
- eGovFrame 4.3 XML, 4.3 Java Config, 5.0 방식의 차이를 Tool 설명에 계속 명시한다.
- 생성 결과 안내에 다음 단계로 `MenuTool`, `AuthTool`을 사용할 수 있음을 표시한다.

`SecurityTemplateTool`의 판단 기준은 다음이다.

> 이 조합만 생성하면 Security가 URL 권한 DB를 읽을 준비가 끝나는가?

예를 들어 `setup-all-war-43-xml`은 단순히 파일 10개를 생성하는 조합이 아니라, 4.3 XML Security 기반과 권한 mapper까지 포함한 상태를 의미해야 한다.

### 7.2 MenuTool

`MenuTool`은 `COMTNPROGRMLIST`, `COMTNMENUINFO` 중심으로 유지해야 한다.

권한 SQL 생성까지 `MenuTool`에 넣으면 메뉴 노출과 접근 제어 책임이 섞인다. 따라서 `MenuTool`은 화면 메뉴 노출과 프로그램 등록에 집중하는 것이 맞다.

강화 방향은 다음과 같다.

- `upperMenuNo` 존재 여부를 검증한다.
- `PROGRM_FILE_NM` 중복 여부를 확인하거나, 중복 확인 절차를 더 강하게 안내한다.
- `URL` 중복 여부를 확인한다.
- `MENU_NO`, `MENU_ORDR` 계산 결과의 기준을 명확히 설명한다.
- 생성 SQL에 사용자 입력값 escape 처리를 적용한다.
- DB 종류별 SQL 차이를 대응하거나 MySQL/MariaDB 전용임을 명시한다.

`MenuTool`의 판단 기준은 다음이다.

> 이 SQL을 실행하면 신규 기능이 eGovFrame 메뉴에 정확히 노출되는가?

### 7.3 AuthTool

`AuthTool`은 `COMTNROLEINFO`, `COMTNAUTHORROLERELATE` 중심으로 유지해야 한다.

이 Tool의 책임은 URL 패턴을 Security 권한 체계에 연결하는 것이다. 메뉴 생성이나 Security 파일 생성까지 같이 처리하면 책임이 흐려진다.

강화 방향은 다음과 같다.

- `urlPrefix` 형식을 검증한다.
- `ROLE_CODE` 자동 계산 결과와 중복 가능성을 안내한다.
- `ROLE_PTTRN` 생성 규칙을 명확히 설명한다.
- `ROLE_ADMIN` 외 추가 권한 그룹을 선택할 수 있는 옵션을 검토한다.
- `securityMapper`가 있어야 권한 DB가 Security에 반영된다는 점을 안내한다.
- SQL 실행 후 서버 재기동 또는 Security 캐시 갱신이 필요함을 계속 안내한다.
- 생성 SQL에 사용자 입력값 escape 처리를 적용한다.

`AuthTool`의 판단 기준은 다음이다.

> 이 SQL을 실행하면 해당 URL이 Security 권한 체계 안에서 통제되는가?

특히 다음 관계가 깨지면 안 된다.

```text
MenuTool 생성 URL:
  /emp/employer/EgovEmployerList.do

AuthTool 생성 ROLE_PTTRN:
  \A/emp/employer/.*\.do.*\Z

Controller 실제 URL:
  /emp/employer/EgovEmployerList.do
```

## 8. Workflow 강화 방향

현재는 3개 Tool이 각각 존재한다.

앞으로 중요한 것은 Tool을 하나로 합치는 것이 아니라, AI가 올바른 순서로 Tool을 호출하도록 워크플로우를 강화하는 것이다.

예를 들어 사용자가 다음과 같이 요청했다고 가정한다.

```text
직원관리 기능을 메뉴에 추가하고 권한까지 설정해줘
```

이때 AI는 내부적으로 다음 순서를 따라야 한다.

```text
1. SecurityTemplateTool 적용 여부 확인
2. securityMapper 포함 여부 확인
3. MenuTool.getMenuStructure()
4. AuthTool.getProgramList()
5. MenuTool.generateMenuInsertSql()
6. AuthTool.generateAuthInsertSql()
7. SQL 실행 순서와 검증 방법 안내
```

추천 아키텍처 방향은 다음과 같다.

| 영역 | 담당 Tool |
|---|---|
| 기반 생성 | `SecurityTemplateTool` |
| DB SQL 생성 | `MenuTool`, `AuthTool` |
| 상태 점검 | `ProjectHealthTool`, `ProjectScannerTool` |
| 사용 순서 안내 | `WorkflowGuideTool` 또는 별도 `SecurityMenuAuthWorkflowTool` |

새로운 Workflow 성격의 Tool을 만든다면, 실제 SQL 생성은 기존 `MenuTool`, `AuthTool`에 맡기고 안내와 검증 체크리스트만 담당하는 것이 좋다.

즉, Workflow Tool은 실행자가 아니라 조율자 역할을 해야 한다.

## 9. 향후 구현 우선순위

앞으로 작업 우선순위는 다음 순서가 적절하다.

```text
1. MenuTool / AuthTool 입력 검증 추가
2. SQL 문자열 escape 처리
3. MenuTool / AuthTool 단위 테스트 추가
4. SecurityTemplateTool 결과와 AuthTool의 securityMapper 의존 관계 문서화
5. WorkflowGuideTool에 Security → Menu → Auth 적용 흐름 추가
6. 통합 시나리오 테스트 문서 작성
```

현재 시점에서 가장 중요한 보완은 `MenuTool`, `AuthTool`의 SQL 생성 안전성이다.

`SecurityTemplateTool`은 이미 조합 완결성과 버전 분기가 상당 부분 정리되어 있으므로, 다음 위험은 생성 SQL을 사용자가 그대로 실행할 때 안전한지 여부에 더 가깝다.

특히 다음 항목은 우선 수정 대상으로 본다.

- null / blank 입력 처리
- 숫자형 입력 검증
- `urlPrefix` 형식 검증
- SQL literal escape
- DB 방언 의존성 명시
- 출력 SQL 스냅샷 테스트

## 10. 최종 방향

최종 방향은 다음과 같이 정리할 수 있다.

> 각 Tool은 단일 책임을 유지한다.  
> 대신 AI가 반드시 올바른 순서로 Tool을 호출하도록 Workflow를 강화한다.  
> 생성 SQL은 운영자가 그대로 실행해도 깨지지 않도록 검증과 escape를 강화한다.

따라서 앞으로의 구현 방향은 Tool 통합이 아니라 Tool 간 관계 정렬이다.

```text
SecurityTemplateTool
  → 보안 기반과 securityMapper 준비

MenuTool
  → 프로그램/메뉴 등록 SQL 생성

AuthTool
  → URL 접근 권한 SQL 생성

WorkflowGuideTool
  → 위 3개 Tool의 호출 순서와 검증 절차 안내
```

