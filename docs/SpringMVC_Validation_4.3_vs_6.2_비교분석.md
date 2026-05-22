# Spring MVC 4.3.x vs 6.2.x Validation 비교 영향 분석

작성일: 2026-05-22
대상: eGovFrame 4.3 (Spring 5.x / javax) ↔ eGovFrame 5.0 (Spring 6.x / jakarta)
참조:
- https://docs.spring.io/spring-framework/docs/4.3.x/spring-framework-reference/html/mvc.html#mvc-ann-validating
- https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-validation.html

---

## 1. 공통 미반영 항목 (4.3 / 6.2 동일)

MCP가 생성하는 CRUD 코드에서 두 버전 공통으로 미반영된 항목.

| 항목 | 4.3.x | 6.2.x |
|---|:---:|:---:|
| VO 제약 어노테이션 (`@NotBlank`, `@Size` 등) | ❌ | ❌ |
| Controller `@Valid` + `BindingResult` | ❌ | ❌ |
| JSP `<form:form>` / `<form:errors>` | ❌ | ❌ |
| 전역 예외 핸들러 (`@ControllerAdvice`) | ❌ | ❌ |

---

## 2. 버전별 차이점

| 구분 | 4.3.x (eGovFrame 4.3) | 6.2.x (eGovFrame 5.0) |
|---|---|---|
| **제약 어노테이션 패키지** | `javax.validation.constraints.*` | `jakarta.validation.constraints.*` |
| **Validator 구현체** | Hibernate Validator **6.x** | Hibernate Validator **8.x** |
| **EL 구현체** | `org.glassfish:jakarta.el:3.0.4` | `org.glassfish:jakarta.el:4.0.2` |
| **`<mvc:annotation-driven/>` 상태** | ✅ 이미 생성됨 — 구현체만 없어 미작동 | 선언 필요 + `LocalValidatorFactoryBean` 명시 등록 필요 |
| **검증 실패 예외** | `MethodArgumentNotValidException` | `MethodArgumentNotValidException` + `HandlerMethodValidationException` (6.1+ 추가) |
| **메서드 레벨 검증** | AOP 기반 (`@Validated` + `MethodValidationPostProcessor`) | Spring MVC 6.1+ **내장 지원** (AOP 불필요) |

---

## 3. 가장 큰 차이 — Hibernate Validator 의존성 누락 방식

### 4.3.x

`<mvc:annotation-driven/>`이 이미 생성되어 있어 **설정은 준비된 상태**.
`validation-api`(API jar)만 포함되어 구현체가 없어 자동 구성이 비활성화됨.
**의존성 추가만으로 즉시 동작**.

```xml
<!-- pom.xml — 4.3.x 추가 필요 -->
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

```gradle
// build.gradle — 4.3.x 추가 필요
implementation 'org.hibernate.validator:hibernate-validator:6.2.5.Final'
implementation 'org.glassfish:jakarta.el:3.0.4'
```

> `<mvc:annotation-driven/>`이 클래스패스에서 Hibernate Validator를 자동 감지하여
> `LocalValidatorFactoryBean`을 등록한다. 별도 빈 선언 불필요.

---

### 6.2.x

`<mvc:annotation-driven/>`만으로는 부족하고 **`LocalValidatorFactoryBean`을 명시 등록**해야 함.

```xml
<!-- context-common.xml 또는 dispatcher-servlet.xml — 6.2.x -->
<bean id="validator"
      class="org.springframework.validation.beanvalidation.LocalValidatorFactoryBean"/>

<mvc:annotation-driven validator="validator"/>

<!-- 메서드 레벨 검증 활성화 (필요 시) -->
<bean class="org.springframework.validation.beanvalidation.MethodValidationPostProcessor">
    <property name="validator" ref="validator"/>
</bean>
```

```xml
<!-- pom.xml — 6.2.x 추가 필요 -->
<dependency>
    <groupId>org.hibernate.validator</groupId>
    <artifactId>hibernate-validator</artifactId>
    <version>8.0.1.Final</version>
</dependency>
<dependency>
    <groupId>org.glassfish</groupId>
    <artifactId>jakarta.el</artifactId>
    <version>4.0.2</version>
</dependency>
```

```gradle
// build.gradle — 6.2.x 추가 필요
implementation 'org.hibernate.validator:hibernate-validator:8.0.1.Final'
implementation 'org.glassfish:jakarta.el:4.0.2'
```

---

## 4. VO 제약 어노테이션 비교

### 4.3.x

```java
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Getter
@Setter
public class EmployerVO {

    @NotBlank
    @Size(max = 20)
    private String emplyrId;

    @NotBlank
    @Size(max = 50)
    private String userNm;

    @NotNull
    private Integer orgId;
}
```

### 6.2.x

```java
import jakarta.validation.constraints.NotBlank;  // javax → jakarta
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class EmployerVO {

    @NotBlank
    @Size(max = 20)
    private String emplyrId;

    @NotBlank
    @Size(max = 50)
    private String userNm;

    @NotNull
    private Integer orgId;
}
```

> 선언 구조는 동일. **패키지명만 `javax` → `jakarta`** 로 변경.

---

## 5. Controller `@Valid` + `BindingResult` — 버전 공통

두 버전 모두 동일한 패턴. 패키지 차이 없음.

```java
// 4.3.x / 6.2.x 공통
@RequestMapping("{{URL_PREFIX}}Regist.do")
public String insertEmployer(
        @ModelAttribute("employerVO") @Valid EmployerVO employerVO,
        BindingResult bindingResult,
        ModelMap model) throws Exception {

    if (bindingResult.hasErrors()) {
        return "employer/EgovEmployerRegist";
    }
    employerService.insertEmployer(employerVO);
    return "forward:/emp/employerList.do";
}
```

> Spring MVC 규칙: `BindingResult`는 `@Valid` 파라미터 **바로 다음**에 위치해야 한다.
> `BindingResult` 없이 검증 실패 시 → `MethodArgumentNotValidException` 발생 → HTTP 400.

---

## 6. JSP `<form:form>` + `<form:errors>` — 버전 공통

두 버전 모두 동일한 Spring form 태그 라이브러리 URI 사용.

```jsp
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form"%>

<form:form modelAttribute="employerVO"
           action="${pageContext.request.contextPath}/emp/employerRegist.do"
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
            <tr>
                <th>조직ID</th>
                <td>
                    <form:input path="orgId"/>
                    <form:errors path="orgId" cssClass="error-msg"/>
                </td>
            </tr>
        </tbody>
    </table>
    <button type="submit">저장</button>
</form:form>
```

---

## 7. 예외 처리 비교

### 4.3.x — 단일 예외 처리로 충분

```java
@ControllerAdvice
public class EgovValidationExceptionHandler {

    // @Valid 실패 (BindingResult 없을 때) → HTTP 400
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            RedirectAttributes redirectAttributes) {

        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("입력값을 확인하세요.");
        redirectAttributes.addFlashAttribute("errorMessage", message);
        return "redirect:/error";
    }
}
```

### 6.2.x — 2개 예외 처리 필요

Spring MVC 6.1+에서 메서드 레벨 검증 지원이 내장되면서
`HandlerMethodValidationException`이 추가됨.

```java
@RestControllerAdvice
public class EgovValidationExceptionHandler
        extends ResponseEntityExceptionHandler {

    // @ModelAttribute @Valid 실패 (BindingResult 없을 때)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> errors.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    // @RequestParam @NotBlank 등 메서드 레벨 제약 실패 (6.1+ 신규)
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        // Visitor 패턴으로 파라미터 유형별 오류 처리
        ex.visitResults(new HandlerMethodValidationException.Visitor() {
            @Override
            public void requestParam(@Nullable RequestParam requestParam,
                                     ParameterValidationResult result) {
                // @RequestParam 검증 실패 처리
            }

            @Override
            public void modelAttribute(@Nullable ModelAttribute modelAttribute,
                                       ParameterErrors errors) {
                // @ModelAttribute 검증 실패 처리
            }

            @Override
            public void other(ParameterValidationResult result) {
                // 기타 파라미터 검증 실패 처리
            }
        });

        return ResponseEntity.badRequest()
            .body(Map.of("error", "입력값을 확인하세요."));
    }
}
```

---

## 8. 메서드 레벨 검증 비교

### 4.3.x — AOP 기반 (`MethodValidationPostProcessor` 필요)

```xml
<!-- context-common.xml -->
<bean class="org.springframework.validation.beanvalidation.MethodValidationPostProcessor">
    <property name="validator" ref="validator"/>
</bean>
```

```java
// Controller 클래스 레벨에 @Validated 선언 필수
@Controller
@Validated
public class EgovEmployerController {

    @GetMapping("/emp/employer")
    public String getEmployer(@PathVariable @NotBlank String emplyrId) {
        // MethodValidationInterceptor가 AOP로 검증
    }
}
```

### 6.2.x — Spring MVC 내장 지원 (`@Validated` 클래스 레벨 불필요)

```java
// 클래스 레벨 @Validated 없이도 메서드 파라미터 제약 동작
@Controller
public class EgovEmployerController {

    @GetMapping("/emp/employer")
    public String getEmployer(
            @PathVariable @NotBlank String emplyrId,  // 자동 검증
            @RequestParam @Size(max = 100) String keyword) {
        // Spring MVC 6.1+ 내장 검증 → HandlerMethodValidationException 발생
    }
}
```

> 6.2.x에서 클래스 레벨 `@Validated`를 선언하면 AOP와 내장 검증이 **중복 실행**될 수 있다.
> Spring 공식 문서: 6.1+ 내장 지원 사용 시 `@Validated` 클래스 선언 제거 권장.

---

## 9. 구현 우선순위 비교

| 우선순위 | 4.3.x | 6.2.x |
|---|---|---|
| **P1** | Hibernate Validator 6.x 의존성 추가 → 즉시 동작 | Hibernate Validator 8.x + `LocalValidatorFactoryBean` 명시 등록 |
| **P1** | VO `javax.validation.*` 어노테이션 자동 생성 | VO `jakarta.validation.*` 어노테이션 자동 생성 |
| **P1** | Controller `@Valid` + `BindingResult` | 동일 |
| **P2** | JSP `<form:form>` + `<form:errors>` | 동일 |
| **P3** | `@ControllerAdvice` — `MethodArgumentNotValidException` 1개 | `ResponseEntityExceptionHandler` 상속 — 2개 예외 처리 |
| **P4** | `MethodValidationPostProcessor` 빈 등록 (AOP 기반) | 불필요 — 내장 지원 |

---

## 10. 현재 구현 반영 상태 (2026-05-22 기준)

| 항목 | 4.3.x | 6.2.x |
|---|:---:|:---:|
| Hibernate Validator 의존성 | ✅ `6.2.5.Final` + `jakarta.el:3.0.4` | ✅ `8.0.1.Final` + `jakarta.el:4.0.2` |
| VO `{{VALIDATION_IMPORT}}` 플레이스홀더 (javax/jakarta 자동 분기) | ✅ | ✅ |
| VO `@NotBlank`/`@NotNull`/`@Size` DB 메타데이터 기반 자동 생성 | ✅ | ✅ |
| Controller `@Valid` + `BindingResult` | ✅ | ✅ |
| JSP `<form:form>` + `form` taglib 전환 | ✅ | ✅ |
| `@ControllerAdvice` 전역 예외 핸들러 생성 코드 포함 | ❌ | ❌ |
| `LocalValidatorFactoryBean` 명시 등록 (`dispatcher-servlet.xml`) | — (불필요) | ❌ |
| `MethodValidationPostProcessor` 빈 등록 | ❌ | — (불필요) |

---

## 11. 잔여 구현 항목

### 공통 (4.3 / 6.2 모두)

| 항목 | 위치 | 내용 |
|---|---|---|
| `EgovValidationExceptionHandler` 생성 | MCP 생성 파일 목록 추가 | 도메인별 `@ControllerAdvice` 파일 신규 생성 |

### 4.3.x 전용

| 항목 | 위치 | 내용 |
|---|---|---|
| `MethodValidationPostProcessor` 빈 등록 | `ProjectInitializrService.contextCommon()` | 메서드 레벨 검증 활성화 (필요 시) |

### 6.2.x 전용

| 항목 | 위치 | 내용 |
|---|---|---|
| `LocalValidatorFactoryBean` + `<mvc:annotation-driven validator="validator"/>` | `ProjectInitializrService.dispatcherServlet()` | 명시적 Validator 빈 등록 |
| `HandlerMethodValidationException` 처리 | `EgovValidationExceptionHandler` | `ResponseEntityExceptionHandler` 상속 + Visitor 패턴 구현 |
