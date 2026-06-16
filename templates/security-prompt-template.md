# Security Generation Request

## User Request

{{userRequest}}

## Selected Tool Flow

1. `WorkflowGuideTool.suggestSecurityMenuAuthWorkflow("")`
2. `SecurityTemplateTool.getSecurityTemplate(...)`
3. 필요한 경우 `MenuTool.getMenuStructure("{{upperMenuNo}}")`
4. 필요한 경우 `AuthTool.getProgramList("{{programKeyword}}")`
5. 필요한 경우 `MenuTool.generateMenuInsertSql(...)`
6. 필요한 경우 `AuthTool.generateAuthInsertSql(...)`

## Security Target

| Key | Value |
| --- | --- |
| Security Type | `{{securityType}}` |
| Package Name | `{{packageName}}` |
| eGovFrame Version | `{{egovVersion}}` |
| Project Type | `{{projectType}}` |
| Output Path | `{{outputPath}}` |
| Login URL | `{{loginUrl}}` |
| Logout URL | `{{logoutUrl}}` |
| Default Target URL | `{{defaultTargetUrl}}` |
| Failure URL | `{{failureUrl}}` |

## Version Rules

### eGovFrame 4.3

- Spring Security 5.x 기준으로 생성한다.
- Java source는 `javax.*` namespace를 사용한다.
- XML Security 방식은 `context-security.xml`의 `<http>` 설정을 사용한다.
- Java Config 방식은 `WebSecurityConfigurerAdapter` 패턴을 사용할 수 있다.

### eGovFrame 5.0

- Spring Security 6.x 기준으로 생성한다.
- Java source는 `jakarta.*` namespace를 사용한다.
- `EgovSecurityConfiguration` import 방식과 `EgovSecurityConfig` Bean 구성을 따른다.
- XML `<http>`와 Java Config `SecurityFilterChain`을 동시에 선언하지 않는다.

## Security Type Selection

| Request | securityType |
| --- | --- |
| 4.3 WAR XML 기본 구성 | `setup-war-43-xml` |
| 4.3 WAR XML 전체 구성 | `setup-all-war-43-xml` |
| 4.3 Java Config 기본 구성 | `setup-war-43-java` |
| 4.3 Java Config 전체 구성 | `setup-all-war-43-java` |
| 5.0 WAR 기본 구성 | `setup-war-50` |
| 5.0 WAR 전체 구성 | `setup-all-war-50` |
| DB 인증 필터만 생성 | `setup-filters` |
| 4.3 핸들러만 생성 | `setup-handlers-43` |

## Generation Rules

- Security XML 방식과 Java Config 방식을 임의로 혼합하지 않는다.
- 4.3 전용 securityType에 5.0 버전을 지정하지 않는다.
- 5.0 전용 securityType에 4.3 버전을 지정하지 않는다.
- 로그인 필터는 Spring Security filter chain보다 앞에 위치해야 한다.
- URL 권한은 `COMTNROLEINFO`, `COMTNAUTHORROLERELATE` 기준으로 생성한다.
- SQL은 반환만 하고 직접 실행하지 않는다.
- SQL 실행 후에는 서버 재기동 또는 Security 캐시 갱신이 필요하다고 안내한다.

## Existing Security Context

{{existingSecurityContext}}

## Required Output

- 생성 또는 반환된 Security 파일 목록
- 파일별 저장 경로
- securityMapper SQL 포함 여부
- 메뉴 등록 필요 여부
- 권한 SQL 필요 여부
- 적용 후 재기동/검증 절차

## Validation Checklist

- [ ] `egovVersion`과 `securityType` 조합이 맞다.
- [ ] `packageName`이 Java 파일 package 선언과 일치한다.
- [ ] XML 방식과 Java Config 방식이 충돌하지 않는다.
- [ ] 로그인/로그아웃 URL이 프로젝트 URL 정책과 일치한다.
- [ ] DB 권한 SQL은 실행용이 아니라 검토용으로 제시된다.
- [ ] 5.0에서는 Jakarta namespace를 사용한다.
- [ ] 4.3에서는 Javax namespace를 사용한다.

## Stop Conditions

- eGovFrame 버전이 불명확하면 생성하지 않는다.
- `projectType`이 불명확하면 생성하지 않는다.
- 기존 Security 방식이 확인되지 않았는데 XML/Java Config를 임의 선택하지 않는다.
- 사용자가 SQL 실행까지 요청해도 Tool은 SQL 생성까지만 수행한다.
