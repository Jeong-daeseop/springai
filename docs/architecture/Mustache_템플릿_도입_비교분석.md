# Mustache/FreeMarker 템플릿 도입 비교분석

작성일: 2026-06-15

## 1. 검토 목적

현재 프로젝트에는 여러 종류의 템플릿이 함께 존재한다.

```text
templates/*.md
src/main/resources/templates/**/*.tpl
CodeTemplateTool 내부 Java text block
```

문서 일부에는 `controller.mustache`, `service.mustache`, `mapper.mustache`, `security-config.mustache` 같은 Mustache 기반 구조도 언급되어 있다. 하지만 현재 실제 구현에는 `.mustache` 파일과 Mustache 렌더링 엔진이 없다. 또한 Java/Spring 코드 생성기 관점에서는 `.ftl`(FreeMarker) 도입도 함께 검토해야 한다.

이 문서는 현재 템플릿 구조와 Mustache/FreeMarker 도입 시 장단점, 적용 우선순위, 권장 전환 방향을 비교하기 위한 자료다.

## 2. 현재 템플릿 구조

### 2.1 루트 `templates/*.md`

현재 루트 `templates/`에는 MCP Prompt 템플릿이 있다.

```text
templates/
├── prompt-template.md
├── crud-prompt-template.md
├── security-prompt-template.md
└── menu-prompt-template.md
```

이 파일들은 Java, JSP, XML 파일을 직접 생성하는 템플릿이 아니다. MCP 클라이언트나 LLM에게 작업 절차, 필요한 컨텍스트, 출력 형식, 검증 조건을 알려주는 작업 지시서다.

예를 들어 `crud-prompt-template.md`는 다음 흐름을 지시한다.

```text
WorkflowGuideTool.suggestNextStep("")
SchemaReaderTool.getTableSchema(...)
SchemaReaderTool.getTableRelations(...)
CrudPromptBuilderTool...
CodeValidatorTool...
GenerationHistoryTool...
ProjectHealthTool...
```

등록 위치:

```text
src/main/java/com/krdevops/springai/config/McpKnowledgeConfig.java
```

등록되는 MCP Prompt:

| Prompt | Template |
| --- | --- |
| `code-generation` | `templates/prompt-template.md` |
| `crud-generation` | `templates/crud-prompt-template.md` |
| `security-generation` | `templates/security-prompt-template.md` |
| `menu-generation` | `templates/menu-prompt-template.md` |

### 2.2 `src/main/resources/templates/**/*.tpl`

이 경로의 `.tpl` 파일은 서버 내부에서 실제 파일을 생성할 때 사용하는 템플릿이다.

```text
src/main/resources/templates/egov/
├── BootApplication.java.tpl
├── BootApplicationTests.java.tpl
├── application.yml.tpl
├── context-common.xml.tpl
├── context-datasource.xml.tpl
├── context-transaction.xml.tpl
├── gitignore.tpl
├── index.jsp.tpl
├── log4j2.xml.tpl
└── logback-spring.xml.tpl

src/main/resources/templates/security/
├── common/*.tpl
├── egov43/*.tpl
└── egov50/*.tpl
```

예시:

```java
package ${packageName};

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("${packageName}")
public class ${className}Application {

    public static void main(String[] args) {
        SpringApplication.run(${className}Application.class, args);
    }
}
```

현재 `.tpl`은 `${key}` 단순 치환 방식이다. 반복, 조건, 리스트 렌더링은 템플릿이 아니라 Java 코드가 담당한다.

### 2.3 `CodeTemplateTool` 내부 Java text block

CRUD 생성 템플릿은 현재 `CodeTemplateTool.java` 내부 Java text block으로 관리된다.

대상:

```text
vo
controller
service
serviceImpl
mapper
mapperXml
jspList
jspDetail
jspRegist
jspUpdt
controlleradvice
```

예시 구조:

```java
private String voTemplate() {
    return """
            package {{PACKAGE}}.service;

            {{VALIDATION_IMPORT}}
            import lombok.Getter;
            import lombok.Setter;

            public class {{DOMAIN}}VO {

            {{VO_FIELDS}}
            }
            """;
}
```

현재 방식은 `{{PLACEHOLDER}}` 문자열을 `CodeService.generateSource()`에서 치환한다.

## 3. `.md`, `.tpl`, `.mustache`, `.ftl` 비교

| 구분 | `.md` Prompt | `.tpl` 파일 템플릿 | `.mustache` 템플릿 | `.ftl` FreeMarker 템플릿 |
| --- | --- | --- | --- | --- |
| 현재 존재 여부 | 있음 | 있음 | 없음 | 없음 |
| 주 위치 | `templates/` | `src/main/resources/templates/` | 도입 시 `src/main/resources/templates/crud/` 후보 | 도입 시 `src/main/resources/templates/crud/` 권장 |
| 목적 | 작업 지시, Tool 흐름 정의 | 실제 파일 생성 | 실제 파일 생성 | 실제 파일 생성 |
| 출력 | 프롬프트 텍스트 | Java/XML/YAML/JSP/SQL | Java/XML/YAML/JSP/SQL | Java/XML/YAML/JSP/SQL |
| 치환 문법 | `{{key}}` | `${key}` | `{{key}}`, `{{#section}}` | `${key}`, `<#list>`, `<#if>`, 매크로 |
| 반복 처리 | 자연어 지시 | Java 코드에서 조립 | 템플릿에서 가능 | 템플릿에서 강력하게 가능 |
| 조건 처리 | 자연어 지시 | Java 코드에서 분기 | 제한적으로 가능 | 템플릿에서 명확하게 가능 |
| 기본 철학 | LLM 작업 절차 | 엔진 없는 단순 치환 | logic-less 템플릿 | 표현력 있는 서버 템플릿 |
| 결정성 | LLM 해석 영향 있음 | 높음 | 높음 | 높음 |
| 적합한 영역 | MCP Prompt | 단순 파일 생성 | 단순 반복/조건 파일 생성 | Java/Spring 코드 생성기 |
| 현재 대체 대상 여부 | 대체 대상 아님 | 유지 권장 | 제한적 신규 후보 | CRUD 생성 1순위 후보 |

## 4. `.md` Prompt와 코드 생성 템플릿의 관계

루트 `templates/*.md`는 Mustache나 FreeMarker 도입으로 대체할 대상이 아니다.

`.md` Prompt의 목적은 다음과 같다.

```text
어떤 Tool을 어떤 순서로 호출할지 정의
어떤 컨텍스트가 필요한지 명시
출력 형식과 검증 절차 안내
LLM이 임의로 경로, 패키지, 버전을 바꾸지 못하게 제약
```

Mustache/FreeMarker의 목적은 다르다.

```text
구조화된 데이터 모델을 입력받아 실제 파일 내용을 결정적으로 렌더링
컬럼 목록 반복
PK 조건 분기
버전별 import 분기
JSP row 반복
Mapper XML resultMap 반복
```

따라서 `.md` Prompt와 `.mustache`/`.ftl`은 대체 관계가 아니라 보완 관계다.

권장 역할 분리:

```text
templates/*.md
  → MCP Prompt 전용

src/main/resources/templates/crud/*.ftl
  → CRUD 실제 코드 생성 전용
```

## 5. `.tpl`, Mustache, FreeMarker 비교

현재 `.tpl` 방식은 단순하고 의존성이 없다.

```text
${packageName}
${className}
${artifactId}
```

같은 단순 치환에는 충분히 적합하다.

적합한 현재 `.tpl` 예:

```text
BootApplication.java.tpl
application.yml.tpl
logback-spring.xml.tpl
gitignore.tpl
index.jsp.tpl
```

반면 Mustache와 FreeMarker는 반복과 조건이 있는 파일에 유리하다.

예시:

```mustache
{{#dependencies}}
<dependency>
    <groupId>{{groupId}}</groupId>
    <artifactId>{{artifactId}}</artifactId>
    {{#version}}<version>{{version}}</version>{{/version}}
</dependency>
{{/dependencies}}
```

`.tpl`로 위 구조를 만들려면 Java 쪽에서 dependency XML 문자열을 미리 조립해야 한다. Mustache를 사용하면 템플릿이 리스트 렌더링을 담당할 수 있다.

FreeMarker는 같은 구조를 더 명시적으로 표현할 수 있다.

```ftl
<#list dependencies as dependency>
<dependency>
    <groupId>${dependency.groupId}</groupId>
    <artifactId>${dependency.artifactId}</artifactId>
    <#if dependency.version??>
    <version>${dependency.version}</version>
    </#if>
</dependency>
</#list>
```

Java/Spring 코드 생성기에서는 FreeMarker가 Mustache보다 실무 적합성이 높다.

이유:

- Java 생태계에서 오래 사용된 서버 사이드 템플릿 엔진이다.
- 조건, 반복, null/default 처리, include, macro 표현력이 충분하다.
- CRUD 생성처럼 컬럼, PK, validation, import, dependency 조건이 많은 파일에 적합하다.
- 템플릿 내부에서 표현 목적의 간단한 분기를 처리할 수 있어 Java 문자열 조립 코드가 줄어든다.
- Spring Boot 프로젝트에 의존성 추가와 테스트 구성이 비교적 단순하다.

정리하면 `.tpl`은 단순 치환 파일에 유지하고, 신규 CRUD 코드 생성 템플릿은 `.ftl`을 우선 검토하는 것이 더 현실적이다. `.mustache`는 logic-less 원칙을 강하게 유지하고 싶거나 여러 언어/런타임에 같은 템플릿을 배포해야 하는 경우의 후보로 두는 편이 맞다.

## 6. `CodeTemplateTool` text block과 템플릿 엔진 비교

CRUD 템플릿은 템플릿 엔진 도입 효과가 가장 큰 영역이다.

현재 `CodeTemplateTool` 방식의 장점:

- 별도 템플릿 로더가 필요 없다.
- Tool과 템플릿이 한 파일에 있어 추적이 쉽다.
- 단순 `String.replace()` 기반이라 동작이 예측 가능하다.
- 외부 의존성이 없다.

현재 방식의 단점:

- Java 파일이 길어지고 템플릿 유지보수가 어렵다.
- JSP/XML/SQL 문법 하이라이팅이 어렵다.
- 템플릿 수정만 해도 Java 재컴파일이 필요하다.
- 반복/조건 표현이 Java 문자열 조립에 의존한다.
- `{{VO_FIELDS}}`, `{{RESULT_MAP_FIELDS}}` 같은 큰 문자열 덩어리를 사전에 만들어야 한다.

Mustache/FreeMarker로 분리할 경우 장점:

- 템플릿 파일을 레이어별로 분리할 수 있다.
- 컬럼 반복, PK 분기, validation 분기가 자연스럽다.
- 템플릿 리뷰와 테스트가 쉬워진다.
- 결정성을 유지하면서 표현력을 높일 수 있다.
- LLM 직접 생성보다 안정적인 서버 렌더링 구조를 유지할 수 있다.

템플릿 엔진 도입 시 단점:

- 렌더러 의존성이 필요하다.
- 렌더링 모델 설계가 필요하다.
- 기존 `{{DOMAIN}}` 또는 `${key}` 플레이스홀더와 엔진 문법이 충돌할 수 있다.
- 단순 문자열 치환보다 디버깅 지점이 늘어난다.
- 복잡한 값 변환은 여전히 Java 모델 생성 코드에서 처리해야 한다.

## 7. CRUD에서 FreeMarker가 유리한 이유

CRUD 생성은 컬럼 기반 반복이 많다.

예:

```text
VO 필드 반복
Validation annotation 조건
Mapper resultMap 반복
INSERT column 반복
INSERT value 반복
UPDATE set 반복
JSP 목록 th/td 반복
JSP 상세 row 반복
JSP form input 반복
PK 컬럼 조건 분기
eGovFrame 4.3/5.0 import 분기
```

Mustache 예시:

```mustache
{{#fields}}
    // {{comment}}
    {{#required}}@NotBlank{{/required}}
    {{#maxLength}}@Size(max = {{maxLength}}){{/maxLength}}
    private {{javaType}} {{javaName}};
{{/fields}}
```

주의: Mustache 구현체에 따라 숫자 `0`을 falsy로 처리할 수 있다. `maxLength`처럼 숫자 값 자체가 조건이 되는 필드는 `hasMaxLength` 같은 boolean 플래그를 별도로 두는 편이 안전하다.

Mapper XML 예시:

```mustache
<resultMap id="{{domainLc}}ResultMap" type="{{packageName}}.service.{{domain}}VO">
{{#fields}}
    {{#pk}}<id property="{{javaName}}" column="{{columnName}}"/>{{/pk}}
    {{^pk}}<result property="{{javaName}}" column="{{columnName}}"/>{{/pk}}
{{/fields}}
</resultMap>
```

이 구조는 현재처럼 `{{VO_FIELDS}}`, `{{RESULT_MAP_FIELDS}}`를 긴 문자열로 미리 만들어 넣는 방식보다 유지보수성이 좋다.

FreeMarker로 표현하면 조건과 기본값을 더 명시적으로 다룰 수 있다.

```ftl
<#list fields as field>
    // ${field.comment}
    <#if field.required>@NotBlank</#if>
    <#if field.maxLength??>@Size(max = ${field.maxLength})</#if>
    private ${field.javaType} ${field.javaName};
</#list>
```

Mapper XML 예시:

```ftl
<resultMap id="${domainLc}ResultMap" type="${packageName}.service.${domain}VO">
<#list fields as field>
    <#if field.pk>
    <id property="${field.javaName}" column="${field.columnName}"/>
    <#else>
    <result property="${field.javaName}" column="${field.columnName}"/>
    </#if>
</#list>
</resultMap>
```

Java/Spring CRUD 생성기에서는 Mustache보다 FreeMarker를 우선하는 편이 좋다. Mustache는 logic-less라 템플릿을 단순하게 유지하기 좋지만, CRUD 생성은 실제로 조건이 많다. 이 조건을 모두 Java 모델 생성 단계로 밀어 넣으면 템플릿 엔진을 도입해도 Java 문자열 조립 코드가 충분히 줄지 않는다.

## 8. FreeMarker 도입 권장 위치

루트 `templates/`는 MCP Prompt 전용이므로 코드 생성 FreeMarker 템플릿을 섞지 않는 것이 좋다.

권장 디렉터리:

```text
src/main/resources/templates/crud/
├── vo.java.ftl
├── controller.java.ftl
├── service.java.ftl
├── service-impl.java.ftl
├── mapper.java.ftl
├── mapper.xml.ftl
├── jsp-list.jsp.ftl
├── jsp-detail.jsp.ftl
├── jsp-regist.jsp.ftl
├── jsp-updt.jsp.ftl
└── controller-advice.java.ftl
```

역할 분리:

```text
templates/*.md
  → MCP Prompt

src/main/resources/templates/egov/*.tpl
  → 프로젝트 초기화 파일 생성

src/main/resources/templates/security/**/*.tpl
  → 보안 파일 생성

src/main/resources/templates/crud/*.ftl
  → CRUD 파일 생성
```

JSP 템플릿은 주의가 필요하다. JSP EL도 `${...}`를 사용하므로 FreeMarker의 `${...}`와 충돌할 수 있다. JSP를 `.ftl`로 관리할 경우 다음 중 하나를 선택해야 한다.

```text
생성 결과 JSP에 ${...}가 그대로 남아야 하는 경우 FreeMarker 템플릿에서는 \${...}로 escape
JSP EL 블록을 통째로 보존해야 하는 경우 <#noparse>...</#noparse> 사용
JSP 템플릿만 현행 .tpl 또는 별도 규칙 유지
```

이 프로젝트에서는 Java, Mapper XML, Service, Controller, VO부터 `.ftl`로 전환하고 JSP는 충돌 처리 규칙을 확정한 뒤 전환하는 순서가 안전하다.

## 9. FreeMarker 렌더링 모델 권장안

FreeMarker를 제대로 쓰려면 문자열 덩어리보다 구조화 모델이 필요하다.

권장 모델 예:

```json
{
  "packageName": "egovframework.let.emp",
  "domain": "Employer",
  "domainLc": "employer",
  "domainKr": "직원",
  "tableName": "COMTNEMPLYRINFO",
  "urlPrefix": "/emp/employer",
  "egovVersion": "5.0",
  "jakartaValidation": true,
  "javaxValidation": false,
  "pk": {
    "columnName": "EMPLYR_ID",
    "javaName": "emplyrId",
    "javaType": "String"
  },
  "fields": [
    {
      "columnName": "EMPLYR_ID",
      "javaName": "emplyrId",
      "javaType": "String",
      "comment": "업무사용자ID",
      "pk": true,
      "required": true,
      "maxLength": 20
    }
  ]
}
```

피해야 할 구조:

```text
Java에서 여전히 VO_FIELDS 같은 긴 문자열을 만들고
FreeMarker에는 그 문자열만 주입
```

이 경우 템플릿 엔진 도입 효과가 작다.

권장 구조:

```text
DB columns
  → FieldModel 리스트
  → CrudTemplateModel
  → FreeMarker 렌더링
  → 파일 저장
  → validateGeneratedCodeDirectory()
```

## 10. 현재 MCP Tool 계약과 호환성

FreeMarker를 도입하더라도 외부 MCP Tool 계약은 유지하는 것이 좋다.

현재 주요 계약:

```text
CodeTemplateTool.getCodeTemplate(layer)
CodeSaverTool.generateSource(layer, valuesJson)
CrudPromptBuilderTool.buildFullCrudPrompt(...)
CodeValidatorTool.validateGeneratedCodeDirectory(...)
```

권장 전환 방식:

```text
외부 Tool 메서드명과 파라미터는 유지
내부 구현만 FreeMarker 렌더링으로 교체
기존 valuesJson 입력은 호환 레이어에서 CrudTemplateModel로 변환
```

이렇게 하면 Claude Desktop/Web, MCP 클라이언트, 기존 문서화된 Tool 흐름이 깨지지 않는다.

## 11. 도입 우선순위

권장 우선순위:

| 우선순위 | 영역 | 판단 |
| --- | --- | --- |
| 1 | CRUD 템플릿 | FreeMarker 도입 효과 가장 큼 |
| 2 | Mapper XML | 컬럼 반복과 PK 분기가 많아 적합 |
| 3 | Controller/VO/Service | 조건부 import와 필드 반복에 적합 |
| 4 | JSP 템플릿 | 목록/상세/form 반복에는 적합하나 JSP EL 충돌 대응 필요 |
| 5 | POM/build.gradle | dependency 반복이 많으면 검토 가능 |
| 6 | Security 템플릿 | 현재 버전별 `.tpl` 분리가 이미 되어 있어 신중 |
| 7 | Boot main/application.yml/logback | 단순 치환이라 현행 `.tpl` 유지 권장 |

## 12. 위험 요소와 대응

### 12.1 템플릿 문법 충돌

현재 CRUD 템플릿은 `{{DOMAIN}}` 같은 플레이스홀더를 사용한다. Mustache도 `{{domain}}` 문법을 사용한다. 현재 `.tpl`은 `${key}`를 사용하고 FreeMarker도 `${key}`를 사용한다.

대응:

```text
기존 대문자 placeholder는 점진 폐기
FreeMarker 변수는 camelCase 사용
예: ${domain}, ${packageName}, <#list fields as field>
기존 .tpl 파일과 .ftl 파일은 확장자로 명확히 분리
.tpl은 기존 단순 치환 렌더러만 처리
.ftl은 FreeMarker 렌더러만 처리
템플릿 로더는 확장자 기반으로 엔진을 라우팅하고, 다른 확장자를 자동 fallback 처리하지 않음
```

### 12.2 JSP EL 충돌

JSP 파일은 `${...}`를 JSP EL로 사용한다. FreeMarker도 `${...}`를 사용하므로 JSP 생성 템플릿에서는 충돌이 발생할 수 있다.

대응:

```text
JSP EL은 \${...}로 escape
JSP EL 구간은 <#noparse>...</#noparse> 사용
충돌이 많은 JSP 템플릿은 초기 전환 대상에서 제외
```

### 12.3 모델 설계 부실

문자열 덩어리를 그대로 주입하면 FreeMarker 도입 효과가 낮다.

대응:

```text
Column metadata를 FieldModel로 구조화
PK, nullable, length, validation, comment를 모델에 포함
템플릿은 반복과 조건만 담당
```

### 12.4 템플릿 복잡도 증가

조건을 템플릿에 과도하게 넣으면 템플릿이 읽기 어려워진다.

대응:

```text
비즈니스 판단은 Java 모델 생성 단계에서 처리
템플릿은 표현과 단순 조건만 담당
버전 차이가 큰 경우 파일을 분리
```

### 12.5 검증 기준 불일치

현재 생성 Tool과 `ProjectHealthTool`, `CodeValidatorTool` 기준이 일부 충돌할 수 있다.

대응:

```text
FreeMarker 전환 시 검증 규칙도 함께 정렬
생성 파일명, 인터페이스명, URL prefix 기준을 하나로 통일
```

## 13. 권장 전환 단계

### 1단계: FreeMarker 의존성 선택

이 프로젝트에서는 FreeMarker를 MVC View 렌더링이 아니라 코드 생성 전용으로 사용한다. 따라서 우선 선택지는 Spring Boot starter가 아니라 순수 FreeMarker 의존성이다.

권장:

```gradle
implementation 'org.freemarker:freemarker:2.3.33'
```

주의:

```text
spring-boot-starter-freemarker는 MVC ViewResolver 자동 설정 목적이 강함
코드 생성 전용이면 starter보다 org.freemarker:freemarker 직접 의존성이 단순함
starter를 선택할 경우 FreeMarker ViewResolver 자동 등록 영향을 별도로 검토
버전은 적용 시점의 Spring Boot/Spring AI 의존성 정합성에 맞춰 고정
```

### 2단계: CRUD FreeMarker 파일 추가

```text
src/main/resources/templates/crud/*.ftl
```

기존 `CodeTemplateTool` text block은 유지하고 신규 렌더러를 병행 구현한다.

### 3단계: 렌더링 모델 추가

예:

```text
CrudTemplateModel
FieldModel
PkModel
ValidationModel
```

### 4단계: 내부 렌더러 추가

예:

```text
CrudFreeMarkerTemplateRenderer
```

최소 인터페이스:

```java
public interface CrudTemplateRenderer {
    String render(String templateName, CrudTemplateModel model);
}
```

구현 책임:

```text
CrudFreeMarkerTemplateRenderer
  - classpath:/templates/crud/ 에서 *.ftl 로드
  - FreeMarker Configuration 사용
  - 템플릿 렌더링 예외를 CodeGenerationException 같은 도메인 예외로 변환

FreeMarkerCodegenConfig
  - 코드 생성 전용 freemarker.template.Configuration 빈 생성
  - defaultEncoding=UTF-8
  - localizedLookup=false 권장
  - MVC ViewResolver 설정과 분리
```

템플릿 이름 규칙:

```text
vo.java.ftl
controller.java.ftl
mapper.xml.ftl
```

호출 예:

```java
renderer.render("vo.java.ftl", model);
```

### 5단계: `valuesJson` 변환 계층 추가

기존 MCP Tool 계약은 유지한다.

```text
generateSource(layer, valuesJson)
```

기존 입력을 받되 내부에서 구조화 모델로 변환한다. 이 책임은 Tool 내부 private 메서드로 숨기기보다 별도 변환 클래스로 분리하는 것이 좋다.

권장 클래스:

```text
CrudTemplateModelFactory
  - valuesJson 파싱
  - 기존 placeholder 값을 구조화 모델로 변환
  - table/field/pk/validation/pagination 모델 구성
  - eGovFrame 4.3/5.0 분기 값 계산

CodeTemplateTool 또는 CodeSaverTool
  - 기존 MCP 파라미터 유지
  - CrudTemplateModelFactory 호출
  - 선택된 CrudTemplateRenderer 호출
```

이 계층이 하위 호환성의 핵심이다. 기존 `valuesJson` 키를 바로 제거하지 않고, 신규 모델 필드와 매핑되는 호환 테이블을 테스트로 고정해야 한다.

### 6단계: 테스트 추가

필수 테스트:

```text
VO 렌더링 테스트
Controller 렌더링 테스트
Mapper XML 렌더링 테스트
JSP 렌더링 테스트
eGovFrame 4.3/5.0 validation import 분기 테스트
PaginationInfo 포함 테스트
Maven compile smoke test
valuesJson 호환 변환 테스트
.tpl/.ftl 엔진 라우팅 테스트
```

### 7단계: 플래그 기반 전환과 롤백

전환 기간에는 기존 text block 렌더러와 FreeMarker 렌더러를 공존시킨다.

권장 플래그:

```yaml
app:
  codegen:
    template-engine: text-block # text-block | freemarker
```

전환 전략:

```text
초기 기본값은 text-block 유지
테스트와 샘플 생성이 안정되면 freemarker를 기본값으로 변경
렌더링 오류 발생 시 어느 엔진에서 실패했는지 명확히 로깅
운영 전환 중에는 설정 변경만으로 text-block으로 롤백 가능하게 유지
전환 완료 후 일정 기간이 지나면 text block 제거 여부를 별도 결정
```

FreeMarker 선택 상태에서 오류가 발생했을 때 조용히 text block으로 자동 fallback하면 생성 결과가 달라진 원인을 숨길 수 있다. 따라서 자동 fallback보다는 플래그 기반 명시 전환과 명시 롤백이 더 안전하다.

## 14. 결론

Mustache와 FreeMarker는 루트 `templates/*.md`를 대체할 대상이 아니다. `.md`는 MCP Prompt 템플릿으로 유지해야 한다.

현재 `.tpl` 파일은 단순한 프로젝트 초기화 파일과 보안 파일 생성에 이미 잘 맞는다. 전면 전환할 필요는 낮다.

코드 생성기 포함 대부분의 Java/Spring 실무에서는 `.ftl` 사용을 권장한다. CRUD는 컬럼 반복, PK 분기, validation 조건, JSP/Mapper XML 반복이 많고, 이 조건을 표현하기에는 Mustache보다 FreeMarker가 더 실용적이다.

`.tpl`은 여러 엔진을 추상화해 외부에 배포하는 라이브러리이거나, 의존성 없이 단순 placeholder만 치환하는 파일에 제한적으로 유지하는 편이 맞다.

권장 결론:

```text
templates/*.md 유지
src/main/resources/templates/egov/*.tpl 유지
src/main/resources/templates/security/**/*.tpl 유지
src/main/resources/templates/crud/*.ftl 신규 도입
CodeTemplateTool API 유지
내부 CRUD 렌더링만 FreeMarker로 점진 전환
Mustache는 logic-less 정책이 필요한 경우의 대안 후보로만 유지
```
