# 생성 코드의 Spring MVC 4.3.x Validation 반영 현황

작성일: 2026-05-22
참조: https://docs.spring.io/spring-framework/docs/4.3.x/spring-framework-reference/html/mvc.html#mvc-ann-validating
대상: eGovFrame 4.3 (Spring Framework 5.x / javax.validation)

---

## 결론

**4개 레이어 미반영, 설정 1개 치명적 누락**

| 항목 | 상태 | 비고 |
|---|:---:|---|
| Hibernate Validator 의존성 | ❌ | `validation-api`(API만) 포함 — 구현체 없어 검증 자체 불가 |
| `<mvc:annotation-driven/>` | ✅ | `dispatcher-servlet.xml`에 존재 — JSR-303 자동 연결 준비됨 |
| VO 제약 어노테이션 | ❌ | `javax.validation.constraints.*` import 없음, 어노테이션 없음 |
| Controller `@Valid` + `BindingResult` | ❌ | 등록/수정 메서드 모두 누락 |
| JSP `<form:form>` / `<form:errors>` | ❌ | 일반 HTML `<form>` 사용 — 오류 표시 불가 |
| `LocalValidatorFactoryBean` 명시 빈 | ⚠️ | `<mvc:annotation-driven/>`으로 자동 구성 가능하나 구현체 없어 무의미 |

---

## 1. 가장 치명적 문제 — Hibernate Validator 의존성 누락

Spring MVC 4.3.x에서 `<mvc:annotation-driven/>`은 JSR-303 구현체가 클래스패스에 있을 때 **자동으로 Validator를 구성**한다. 그러나 현재 생성되는 `pom.xml` / `build.gradle`에는 **API 전용 jar만** 포함되어 있어 실제 검증이 동작하지 않는다.

### 현재 생성되는 WAR pom.xml

```xml
<!-- API 전용 — 검증 로직 없음 -->
<dependency>
    <groupId>javax.validation</groupId>
    <artifactId>validation-api</artifactId>
    <version>2.0.1.Final</version>
</dependency>
<!-- Hibernate Validator(JSR-380 구현체) 없음 → @NotBlank 등 모두 무시됨 -->
```

### 권장 추가 의존성

```xml
<!-- JSR-380 구현체 (Spring 5.x / eGovFrame 4.3 호환) -->
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
    <version>6.2.5.Final</version>
</dependency>
<dependency>
    <groupId>org.glassfish</groupId>
    <artifactId>jakarta.el</artifactId>
    <version>3.0.4</version>
</dependency>
```

### 권장 추가 의존성 (Gradle)

```gradle
implementation 'org.hibernate.validator:hibernate-validator:6.2.5.Final'
implementation 'org.glassfish:jakarta.el:3.0.4'
```

> **핵심**: Hibernate Validator가 없으면 VO에 `@NotBlank`를 추가해도 전혀 검증되지 않는다.
> 이 항목이 해결되지 않으면 이하 모든 수정이 무의미하다.

---

## 2. `dispatcher-servlet.xml` — `<mvc:annotation-driven/>` 현황

```xml
<!-- 현재 생성 — 존재함 (✅) -->
<mvc:annotation-driven/>
```

`<mvc:annotation-driven/>`은 클래스패스에 JSR-303 구현체가 있을 때 자동으로 `LocalValidatorFactoryBean`을 등록한다. 선언 자체는 올바르나, **Hibernate Validator가 없어** 자동 구성이 활성화되지 않는다.

### Hibernate Validator 추가 후 동작 흐름

```
<mvc:annotation-driven/>
    └─ 클래스패스에 hibernate-validator 탐지
        └─ LocalValidatorFactoryBean 자동 등록
            └─ @Valid / @Validated 어노테이션 동작
```

### 커스텀 Validator가 필요한 경우 명시 등록

```xml
<!-- context-common.xml — 커스텀 Validator 적용 시 -->
<bean id="validator"
      class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean"/>

<!-- dispatcher-servlet.xml — 커스텀 Validator 연결 -->
<mvc:annotation-driven validator="validator"/>
```

---

## 3. VO 템플릿 (`voTemplate()`) — `javax.validation.constraints.*` 미적용

### 현재 생성 코드

```java
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

// ❌ javax.validation.constraints.* import 없음

@Getter
@Setter
public class {{DOMAIN}}VO {

    private String emplyrId;   // @NotBlank 없음
    private String userNm;     // @Size 없음
    private Integer orgId;     // @NotNull 없음

    private int pageIndex = 1;
    ...
}
```

### 문제점

- Spring MVC 4.3.x 기준 패키지: `javax.validation.constraints.*` (5.x·6.x와 동일)
- `CrudPromptBuilderService.buildVoFields()`가 `IS_NULLABLE`, `CHARACTER_MAXIMUM_LENGTH`를 DB에서 조회하고 있음에도 제약 어노테이션으로 변환하지 않음

### 권장 코드

```java
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

@Getter
@Setter
public class {{DOMAIN}}VO {

    @NotBlank
    @Size(max = 20)
    private String emplyrId;

    @NotBlank
    @Size(max = 50)
    private String userNm;

    @NotNull
    private Integer orgId;

    private int pageIndex = 1;
    ...
}
```

### `buildVoFields()` 권장 수정 (`CrudPromptBuilderService.java:142`)

```java
// 현재 — IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH 무시
private String buildVoFields(List<Map<String, Object>> columns) {
    for (Map<String, Object> col : columns) {
        String javaType = toJavaType((String) col.get("DATA_TYPE"));
        sb.append("    private ").append(javaType).append(" ").append(field).append(";\n");
    }
}

// 권장 — 조회한 메타데이터를 어노테이션으로 변환
private String buildVoFields(List<Map<String, Object>> columns) {
    for (Map<String, Object> col : columns) {
        String javaType = toJavaType((String) col.get("DATA_TYPE"));
        String nullable = (String) col.get("IS_NULLABLE");      // "YES" / "NO"
        Object maxLen   = col.get("CHARACTER_MAXIMUM_LENGTH");

        // NOT NULL 컬럼 → @NotBlank(String) / @NotNull(기타)
        if ("NO".equals(nullable)) {
            sb.append("    @")
              .append("String".equals(javaType) ? "NotBlank" : "NotNull")
              .append("\n");
        }
        // 최대 길이 → @Size(max = N)
        if (maxLen != null && "String".equals(javaType)) {
            sb.append("    @Size(max = ").append(maxLen).append(")\n");
        }

        sb.append("    private ").append(javaType).append(" ").append(field).append(";\n");
    }
}
```

---

## 4. Controller 템플릿 (`controllerTemplate()`) — `@Valid` + `BindingResult` 누락

### 현재 생성 코드

```java
// ❌ 등록 — @ModelAttribute 없음, @Valid 없음, BindingResult 없음
@RequestMapping("{{URL_PREFIX}}Regist.do")
public String insert{{DOMAIN}}(
        {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        ModelMap model) throws Exception {

    {{DOMAIN_LC}}Service.insert{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}

// ❌ 수정 — 동일하게 모두 누락
@RequestMapping("{{URL_PREFIX}}Updt.do")
public String update{{DOMAIN}}(
        {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        ModelMap model) throws Exception {

    {{DOMAIN_LC}}Service.update{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}
```

### 메서드별 Validation 적용 현황

| 메서드 | `@ModelAttribute` | `@Valid` | `BindingResult` |
|---|:---:|:---:|:---:|
| `select{{DOMAIN}}List` (목록) | ✅ | ❌ | ❌ |
| `select{{DOMAIN}}` (상세) | ✅ | ❌ | ❌ |
| `insert{{DOMAIN}}View` (등록 화면) | ✅ | ❌ | — |
| **`insert{{DOMAIN}}`** (등록 처리) | **❌** | **❌** | **❌** |
| `update{{DOMAIN}}View` (수정 화면) | ✅ | ❌ | — |
| **`update{{DOMAIN}}`** (수정 처리) | **❌** | **❌** | **❌** |
| `delete{{DOMAIN}}` (삭제) | ❌ | ❌ | — |

> Spring MVC 4.3.x 규칙: `BindingResult`는 `@Valid` 파라미터 **바로 다음**에 위치해야 한다.
> `BindingResult` 없이 검증 실패 시 → `MethodArgumentNotValidException` 발생 → HTTP 400 응답.

### 권장 코드

```java
// 등록
@RequestMapping("{{URL_PREFIX}}Regist.do")
public String insert{{DOMAIN}}(
        @ModelAttribute("{{DOMAIN_LC}}VO") @Valid {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        BindingResult bindingResult,
        ModelMap model) throws Exception {

    if (bindingResult.hasErrors()) {
        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Regist";  // 폼 재표시
    }
    {{DOMAIN_LC}}Service.insert{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}

// 수정
@RequestMapping("{{URL_PREFIX}}Updt.do")
public String update{{DOMAIN}}(
        @ModelAttribute("{{DOMAIN_LC}}VO") @Valid {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        BindingResult bindingResult,
        ModelMap model) throws Exception {

    if (bindingResult.hasErrors()) {
        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Updt";  // 폼 재표시
    }
    {{DOMAIN_LC}}Service.update{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}
```

---

## 5. JSP 등록/수정 템플릿 — `<form:form>` / `<form:errors>` 미사용

### 현재 생성 코드

```jsp
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%-- ❌ Spring form 태그 라이브러리 없음 --%>

<form name="{{DOMAIN_LC}}Form" action="<c:url value='{{URL_PREFIX}}Regist.do'/>" method="post">
    <table>
        <tbody>
            <tr><th>이름</th><td>
                <input type="text" name="userNm" value="${userNm}"/>
                <%-- ❌ <form:errors> 없음 — 오류 메시지 표시 불가 --%>
            </td></tr>
        </tbody>
    </table>
    <button type="submit">저장</button>
</form>
```

### 문제점

- `<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>` 없음
- `<form:form modelAttribute>` 대신 일반 HTML `<form>` 사용
- `<form:errors>` 없어 `BindingResult`에 담긴 오류를 화면에 표시할 수 없음
- `<form:input>` 대신 `<input type="text">` 사용 — Spring MVC 바인딩 오류 CSS 클래스 미적용

### 권장 코드

```jsp
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"    uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>

<form:form modelAttribute="{{DOMAIN_LC}}VO"
           action="${pageContext.request.contextPath}{{URL_PREFIX}}Regist.do"
           method="post">
    <table>
        <tbody>
            <tr>
                <th>이름</th>
                <td>
                    <form:input path="userNm" cssClass="input-text"/>
                    <form:errors path="userNm" cssClass="error-msg"/>
                </td>
            </tr>
        </tbody>
    </table>
    <div>
        <button type="submit">저장</button>
        <a href="<c:url value='{{URL_PREFIX}}List.do'/>">취소</a>
    </div>
</form:form>
```

---

## 6. Spring MVC 4.3.x vs 6.2.x 차이점 비교

| 항목 | 4.3.x (eGovFrame 4.3) | 6.2.x (eGovFrame 5.0) |
|---|---|---|
| 제약 어노테이션 패키지 | `javax.validation.constraints.*` | `jakarta.validation.constraints.*` |
| Validator 구현체 | Hibernate Validator **6.x** | Hibernate Validator **8.x** |
| `<mvc:annotation-driven/>` | 자동 JSR-303 구성 | 동일 (Jakarta EE 기반) |
| `BindingResult` 위치 규칙 | `@Valid` 바로 다음 | 동일 |
| JSP form 태그 | `http://www.springframework.org/tags/form` | 동일 |
| 메서드 레벨 검증 예외 | `MethodArgumentNotValidException` | 추가로 `HandlerMethodValidationException` |

---

## 7. 추가 구현 필요 항목 요약

| 우선순위 | 위치 | 항목 |
|---|---|---|
| **P1** | `ProjectInitializrService.warPomXml()` / `warBuildGradle()` | `hibernate-validator:6.2.5.Final` + `jakarta.el:3.0.4` 의존성 추가 (eGovFrame 4.3용) |
| **P1** | `CodeTemplateTool.voTemplate()` | `javax.validation.constraints.*` import 추가 |
| **P1** | `CrudPromptBuilderService.buildVoFields()` | `IS_NULLABLE` → `@NotBlank`/`@NotNull`, `CHARACTER_MAXIMUM_LENGTH` → `@Size` 자동 생성 |
| **P1** | `CodeTemplateTool.controllerTemplate()` | 등록/수정 메서드에 `@ModelAttribute @Valid` + `BindingResult bindingResult` 추가 |
| **P2** | `CodeTemplateTool.jspRegistTemplate()` | `form` taglib 추가, `<form:form modelAttribute>` + `<form:errors>` 전환 |
| **P2** | `CodeTemplateTool.jspUpdtTemplate()` | 동일 — `<form:form>` + `<form:errors>` 전환 |
| **P3** | `ProjectInitializrService.contextCommon()` | 커스텀 Validator 필요 시 `LocalValidatorFactoryBean` 빈 등록 추가 |
| **P3** | MCP 생성 파일 목록 | `EgovValidationExceptionHandler.java` 신규 파일 생성 추가 |

---

## 8. 구현 영향 평가

| 항목 | 현재 상태 | 미반영 시 영향 |
|---|---|---|
| Hibernate Validator 의존성 | ❌ 없음 | `@NotBlank` 등 선언해도 **검증 자체가 실행되지 않음** |
| VO 제약 어노테이션 | ❌ 없음 | 빈 문자열·null이 DB까지 전달 → DB 제약 위반 시 500 |
| Controller `@Valid` | ❌ 없음 | 잘못된 입력이 Service/DB까지 전달 |
| `BindingResult` | ❌ 없음 | 검증 실패 시 `MethodArgumentNotValidException` → HTTP 400 |
| JSP `<form:errors>` | ❌ 없음 | 입력 오류를 사용자에게 표시할 수 없어 UX 불가 |

> **우선순위 핵심**:
> `hibernate-validator` 의존성 추가(P1-1) 없이는 다른 모든 항목을 구현해도 동작하지 않는다.
> P1 4개 항목을 함께 완료해야 end-to-end 검증 흐름이 완성된다.