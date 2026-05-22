# 생성 코드의 Spring MVC 6.2 Validation 반영 현황

작성일: 2026-05-22
참조: https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html

---

## 결론

**전혀 반영 안 됨 — 5개 레이어 전체 누락**

MCP가 생성하는 eGovFrame 5.x CRUD 소스에 Spring MVC 6.2 Validation이 적용되지 않음.
VO에 제약 어노테이션이 없고, Controller에 `@Valid` / `BindingResult`가 없으며,
JSP 폼에 오류 표시 태그가 없어 사용자 입력값이 전혀 검증되지 않는 상태.

---

## 1. VO 템플릿 (`voTemplate()`)

### 현재 생성 코드

```java
import lombok.Getter;
import lombok.Setter;
import org.egovframe.rte.ptl.mvc.tags.ui.pagination.PaginationInfo;

@Getter
@Setter
public class {{DOMAIN}}VO {

    private String emplyrId;   // @NotBlank 없음
    private String userNm;     // @NotBlank 없음
    private Integer age;       // @Min / @Max 없음

    // 페이징/검색 공통 필드
    private int pageIndex = 1;
    ...
}
```

### 문제점

- `jakarta.validation.constraints.*` import 없음
- 모든 필드에 제약 어노테이션 전혀 없음
- `CrudPromptBuilderService.buildVoFields()`가 `IS_NULLABLE`, `CHARACTER_MAXIMUM_LENGTH`를 DB에서 조회하고 있음에도 제약 어노테이션으로 변환하지 않음

### 권장 코드

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private Integer age;
    ...
}
```

---

## 2. Controller 템플릿 (`controllerTemplate()`)

### 현재 생성 코드

```java
// 등록 — @ModelAttribute, @Valid, BindingResult 모두 없음
@RequestMapping("{{URL_PREFIX}}Regist.do")
public String insert{{DOMAIN}}(
        {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        ModelMap model) throws Exception {

    {{DOMAIN_LC}}Service.insert{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}

// 수정 — 동일하게 누락
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
| `insert{{DOMAIN}}View` (등록화면) | ✅ | ❌ | — |
| **`insert{{DOMAIN}}`** (등록 처리) | **❌** | **❌** | **❌** |
| `update{{DOMAIN}}View` (수정화면) | ✅ | ❌ | — |
| **`update{{DOMAIN}}`** (수정 처리) | **❌** | **❌** | **❌** |
| `delete{{DOMAIN}}` (삭제) | ❌ | ❌ | — |

> Spring MVC 6.2 규칙: `@ModelAttribute` + `@Valid` 파라미터 바로 뒤에 `BindingResult`를 선언해야 한다.
> `BindingResult`가 없으면 검증 실패 시 `MethodArgumentNotValidException`이 발생하여 500 오류로 처리된다.

### 권장 코드

```java
// 등록
@RequestMapping("{{URL_PREFIX}}Regist.do")
public String insert{{DOMAIN}}(
        @ModelAttribute @Valid {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        BindingResult bindingResult,
        ModelMap model) throws Exception {

    if (bindingResult.hasErrors()) {
        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Regist";
    }
    {{DOMAIN_LC}}Service.insert{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}

// 수정
@RequestMapping("{{URL_PREFIX}}Updt.do")
public String update{{DOMAIN}}(
        @ModelAttribute @Valid {{DOMAIN}}VO {{DOMAIN_LC}}VO,
        BindingResult bindingResult,
        ModelMap model) throws Exception {

    if (bindingResult.hasErrors()) {
        return "{{DOMAIN_LC}}/Egov{{DOMAIN}}Updt";
    }
    {{DOMAIN_LC}}Service.update{{DOMAIN}}({{DOMAIN_LC}}VO);
    return "forward:{{URL_PREFIX}}List.do";
}
```

---

## 3. JSP 등록/수정 템플릿 (`jspRegistTemplate`, `jspUpdtTemplate`)

### 현재 생성 코드

```jsp
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%-- Spring form 태그 없음 — 오류 표시 불가 --%>

<form name="{{DOMAIN_LC}}Form" action="<c:url value='{{URL_PREFIX}}Regist.do'/>" method="post">
    <table>
        <tbody>
            <tr><th>필드명</th><td>
                <input type="text" name="userNm" value="${userNm}"/>
                <%-- 오류 메시지 표시 영역 없음 --%>
            </td></tr>
        </tbody>
    </table>
    <button type="submit">저장</button>
</form>
```

### 문제점

- `<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>` 없음
- `<form:form modelAttribute>` 대신 일반 HTML `<form>` 사용
- `<form:errors>` 없어 `BindingResult` 오류를 사용자에게 표시할 수 없음
- 입력 필드가 `<form:input>` 아닌 `<input type="text">`라 Spring MVC 바인딩 오류 클래스 적용 불가

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
                <th>필드명</th>
                <td>
                    <form:input path="userNm"/>
                    <form:errors path="userNm" cssClass="error"/>
                </td>
            </tr>
        </tbody>
    </table>
    <button type="submit">저장</button>
</form:form>
```

---

## 4. `buildVoFields()` — 조회 정보 미활용

`CrudPromptBuilderService.java:127` 의 쿼리에서 `IS_NULLABLE`, `CHARACTER_MAXIMUM_LENGTH`를 이미 SELECT 하고 있지만, `buildVoFields()`에서 전혀 활용하지 않음.

### 현재 코드 (`CrudPromptBuilderService.java:142`)

```java
private String buildVoFields(List<Map<String, Object>> columns) {
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> col : columns) {
        String field    = toCamelCase((String) col.get("COLUMN_NAME"));
        String javaType = toJavaType((String) col.get("DATA_TYPE"));
        String comment  = col.get("COLUMN_COMMENT") != null ? (String) col.get("COLUMN_COMMENT") : "";
        if (!comment.isEmpty()) sb.append("    // ").append(comment).append("\n");
        sb.append("    private ").append(javaType).append(" ").append(field).append(";\n");
        // IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH 무시
    }
    return sb.toString();
}
```

### 권장 코드

```java
private String buildVoFields(List<Map<String, Object>> columns) {
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> col : columns) {
        String field    = toCamelCase((String) col.get("COLUMN_NAME"));
        String javaType = toJavaType((String) col.get("DATA_TYPE"));
        String nullable = (String) col.get("IS_NULLABLE");          // "YES" / "NO"
        Object maxLen   = col.get("CHARACTER_MAXIMUM_LENGTH");
        String comment  = col.get("COLUMN_COMMENT") != null ? (String) col.get("COLUMN_COMMENT") : "";

        if (!comment.isEmpty()) sb.append("    // ").append(comment).append("\n");

        // IS_NULLABLE = NO → @NotBlank (String) / @NotNull (기타)
        if ("NO".equals(nullable)) {
            sb.append("    @").append("String".equals(javaType) ? "NotBlank" : "NotNull").append("\n");
        }
        // CHARACTER_MAXIMUM_LENGTH → @Size(max = N)
        if (maxLen != null && "String".equals(javaType)) {
            sb.append("    @Size(max = ").append(maxLen).append(")\n");
        }

        sb.append("    private ").append(javaType).append(" ").append(field).append(";\n");
    }
    return sb.toString();
}
```

---

## 5. Spring MVC 설정 — `LocalValidatorFactoryBean` 미포함

`ProjectInitializrService`가 생성하는 `context-common.xml`에 Validator 빈 등록이 없어 Spring MVC가 Bean Validation을 인식하지 못함.

### 추가 필요 설정 (context-common.xml)

```xml
<!-- Bean Validation 활성화 -->
<bean id="validator"
      class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean"/>

<!-- @Validated 메서드 레벨 검증 활성화 (Spring 6.x AOP 방식) -->
<bean class="org.springframework.validation.beanvalidation.MethodValidationPostProcessor">
    <property name="validator" ref="validator"/>
</bean>
```

### 추가 필요 설정 (dispatcher-servlet.xml)

```xml
<mvc:annotation-driven validator="validator"/>
```

---

## 6. 예외 처리 — `@ControllerAdvice` 미생성

MCP가 생성하는 코드에 `@ControllerAdvice` 글로벌 예외 핸들러가 없어, `@Valid` 검증 실패 시 Spring 기본 오류 페이지가 노출됨.

### Spring MVC 6.2 예외 유형

| 예외 클래스 | 발생 시점 |
|---|---|
| `MethodArgumentNotValidException` | `@ModelAttribute @Valid` 검증 실패 (`BindingResult` 없을 때) |
| `HandlerMethodValidationException` | `@RequestParam @NotBlank` 등 메서드 레벨 제약 실패 |

### 권장 추가 파일 — `EgovValidationExceptionHandler.java`

```java
package {{PACKAGE}}.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class EgovValidationExceptionHandler {

    @ExceptionHandler(HandlerMethodValidationException.class)
    public String handleValidation(HandlerMethodValidationException ex,
                                   RedirectAttributes redirectAttributes) {
        String message = ex.getAllValidationResults().stream()
            .flatMap(r -> r.getResolvableErrors().stream())
            .map(e -> e.getDefaultMessage())
            .findFirst()
            .orElse("입력값을 확인하세요.");
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:/error";
    }
}
```

---

## 7. 추가 구현 필요 항목 요약

| 우선순위 | 위치 | 항목 |
|---|---|---|
| **P1** | `CodeTemplateTool.voTemplate()` | `jakarta.validation.constraints.*` import 추가 |
| **P1** | `CrudPromptBuilderService.buildVoFields()` | `IS_NULLABLE` → `@NotBlank`/`@NotNull`, `CHARACTER_MAXIMUM_LENGTH` → `@Size` 자동 생성 |
| **P1** | `CodeTemplateTool.controllerTemplate()` | 등록/수정 메서드에 `@ModelAttribute @Valid` + `BindingResult` 추가 |
| **P2** | `CodeTemplateTool.jspRegistTemplate()` | `<form:form modelAttribute>` + `<form:errors>` 태그 전환, `form` taglib 추가 |
| **P2** | `CodeTemplateTool.jspUpdtTemplate()` | 동일 — `<form:form>` + `<form:errors>` 전환 |
| **P3** | `ProjectInitializrService` WAR 설정 생성 | `context-common.xml` — `LocalValidatorFactoryBean` + `MethodValidationPostProcessor` 빈 등록 |
| **P3** | `ProjectInitializrService` WAR 설정 생성 | `dispatcher-servlet.xml` — `<mvc:annotation-driven validator="validator"/>` 추가 |
| **P4** | MCP 생성 파일 목록 | `EgovValidationExceptionHandler.java` 신규 파일 생성 추가 |

---

## 8. 구현 영향 평가

| 항목 | 현재 상태 | 미반영 시 영향 |
|---|---|---|
| VO 제약 어노테이션 | ❌ 없음 | 빈 문자열·null이 DB INSERT → DB 제약 위반으로 500 오류 |
| Controller `@Valid` | ❌ 없음 | 잘못된 입력이 Service까지 전달 |
| `BindingResult` | ❌ 없음 | 검증 실패 시 `MethodArgumentNotValidException` → 500 오류 |
| JSP `<form:errors>` | ❌ 없음 | 오류 메시지를 사용자에게 표시 불가 |
| Validator 빈 등록 | ❌ 없음 | Bean Validation 자체가 동작하지 않음 |

> **핵심**: Validator 빈 등록이 없으면 `@NotBlank` 등을 VO에 추가해도 실제로 검증이 실행되지 않는다.
> P3 항목(설정 빈 등록)이 P1 항목(어노테이션 추가)보다 선행되어야 전체가 동작한다.