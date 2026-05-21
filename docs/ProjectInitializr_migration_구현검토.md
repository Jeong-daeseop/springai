# ProjectInitializr — 4.3 ↔ 5.0 동시 생성기 구현 검토

작성일: 2026-05-21
최종 업데이트: 2026-05-21 (전 항목 구현 완료)
목적: eGovFrame 4.3/5.0 완전한 동시 생성기(generator) 수준 달성을 위한 추가 구현 항목 검토

---

## 전제: 현재 구현 수준 (최초 분석 시점)

~~`isLatest` 단일 boolean 플래그로 모든 분기를 제어하는 구조.~~

```java
// ✅ 제거됨 — Capability Matrix로 전면 교체 (2026-05-21)
boolean isLatest = "latest".equalsIgnoreCase(egovVersion)
                || "5.0".equalsIgnoreCase(egovVersion)
                || (egovVersion != null && egovVersion.startsWith("5."));
```

~~이 플래그가 참조하는 대상: artifact ID / Multipart / web.xml namespace / servlet-api 의존성 /
Java 버전 / JSTL 의존성 / Boot starter 버전 — 총 7개 분기점.~~

**현재 구조 (2026-05-21 이후)**: `Spec` 레코드가 `String egovVersion`을 보유하며,
`supportsJakarta(v)` · `supportsSpring6(v)` 등 8개 capability 메서드로 각 분기점을 독립 제어.

---

## 항목별 현재 구현 상태 대조

### 항목 1 — Java Runtime Version 분기 ✅ 이미 구현됨

**사전 제안**: Java 버전 분기가 빠져 있다고 언급됐으나, 코드에 이미 구현되어 있습니다.

```java
// 상수 선언
private static final String JAVA_11 = "11";
private static final String JAVA_17 = "17";

// Gradle toolchain — 이미 분기됨
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(%s)  // 4.3→11, 5.0→17
    }
}

// Maven — 이미 분기됨
<java.version>%s</java.version>   // 4.3→11, 5.0→17
```

**판정**: 추가 구현 불필요.

---

### 항목 2 — javax → jakarta import 전환 ✅ 영향 없음 확인

**최초 판단**: `CodeTemplateTool` Controller 템플릿에 `javax.servlet` import가 있어 분기 필요.

**실제 확인 결과**: `CodeTemplateTool.controllerTemplate()`이 `@ModelAttribute` / `ModelMap` 방식을
사용하고 있어 `javax.servlet.http.*` import 자체가 없음 — 분기 불필요.

```java
// CodeTemplateTool 실제 Controller 템플릿 — javax import 없음
public String {{DOMAIN}}List(@ModelAttribute {{DOMAIN}}VO {{DOMAIN_LC}}VO, ModelMap model) { ... }
```

**판정**: 추가 구현 불필요. CRUD 소스에 `HttpServletRequest`를 직접 사용하는 패턴이
추가될 경우 그 시점에 `egovVersion` 파라미터 전달 구조를 도입.

**우선순위**: ~~★★★★★~~ → 해당 없음

---

### 항목 3 — Tomcat 버전 분기 ⚠️ 암묵적 처리 (명시적 처리 불필요)

**현재 상태**: Tomcat 버전을 직접 감지하는 로직은 없으나, egovVersion 선택 결과로
javax/jakarta 계열이 자동 결정되어 **올바른 Tomcat 대상 버전에 맞는 파일이 생성됨**.

| egovVersion | 생성 servlet-api | 맞는 Tomcat |
|---|---|---|
| 4.3 | `javax.servlet-api:4.0.1` | Tomcat 9 이하 |
| 5.0/latest | `jakarta.servlet-api:6.0.0` | Tomcat 10+ 필수 |

추가 구현보다 **가이드 문서에 Tomcat 버전 불일치 경고**를 명시하는 것이 실용적.

**판정**: 추가 구현 불필요, 가이드 보강으로 충분.

---

### 항목 4 — Spring Security 설정 방식 분기 ⚠️ 별도 Tool로 분리됨

**현재 상태**: `initializeProject()`는 Security 설정 파일을 생성하지 않음.
`SecurityTemplateTool.getSecurityTemplate(securityType, packageName, egovVersion)`이
egovVersion 파라미터를 받아 버전별 Security 템플릿 반환.

```
WebSecurityConfigurerAdapter (4.x) → SecurityFilterChain Bean (5.x/6.x)
```

**판단**: 두 가지 선택지가 있음.

| 선택지 | 장점 | 단점 |
|---|---|---|
| 현행 유지 (SecurityTemplateTool 별도) | 관심사 분리, Security만 교체 가능 | 프로젝트 생성 후 추가 Tool 호출 필요 |
| initializeProject에 통합 | 생성 즉시 완전한 Security 골격 | Service 비대화, 선택적 Security 대응 어려움 |

**권장**: 현행 유지. 단 `buildResult()` 다음 단계에 "SecurityTemplateTool 호출" 안내 추가.

---

### 항목 5 — JSTL / JSP 의존성 분기 ✅ 전체 완료 (구현체 누락 수정 포함 2026-05-21)

**의존성 분기 — 구현 완료**:

```java
// warBuildGradle()·warPomXml() 내 분기됨
// 4.3: javax.servlet:jstl:1.2 (API+구현체 통합 JAR)
// 5.0: jakarta.servlet.jsp.jstl-api:3.0.0 (API)
//    + org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1 (구현체 — Tomcat 10+ 필수)
```

**배포 테스트 결과 (Tomcat 10, 2026-05-21)**:
- 구현체 없이 배포 시: `JasperException: 절대 URI [http://java.sun.com/jsp/jstl/core]을 찾을 수 없습니다` — 런타임 장애 확인
- `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` 추가 후: 정상 작동
- Glassfish 구현체가 구 URI(`http://java.sun.com/jsp/jstl/core`) 하위 호환 지원 → taglib URI 분기 불필요 확인

**JSP taglib — 깨진 taglib 제거 완료 (2026-05-21)**:

`jspRegistTemplate()`에 있던 `spring-modules-validation` `commons-validator` taglib URI 제거.
Spring 5+ 미지원 라이브러리로, 런타임 오류의 원인이었음.

```jsp
<%-- 제거됨 (Spring 5+ 미지원) —%>
<%@ taglib prefix="validator" uri="http://www.springmodules.org/tags/commons-validator"%>
```

**판정**: 추가 구현 불필요.

---

### 항목 6 — Bean Validation API 분기 ✅ 구현 완료 (2026-05-21)

**구현 내용**:

```java
// warPomXml() / warBuildGradle() — javax/jakarta 분기
String validationDep = supportsJakarta(s.egovVersion)
    ? "jakarta.validation:jakarta.validation-api:3.0.2"   // 5.0
    : "javax.validation:validation-api:2.0.1.Final";      // 4.3

// bootPomXml() / bootBuildGradle() — Boot BOM 버전 관리
spring-boot-starter-validation   // 4.3(Boot 2.x)·5.0(Boot 3.x) 공통
```

**판정**: 추가 구현 불필요.

---

### 항목 7 — Logging Framework 분기 ✅ 이미 구현됨

**현재 상태**: 이미 projectType에 따라 분기됨.

```java
// WAR → log4j2.xml 생성
writeFile(root, "src/main/resources/log4j2.xml", log4j2Xml(projectName), ...);

// Boot → logback-spring.xml 생성
writeFile(root, "src/main/resources/logback-spring.xml", logbackSpringXml(projectName), ...);
```

두 파일 모두 콘솔 + 롤링 파일 appender, SLF4J 기반으로 생성됨.

**판정**: 추가 구현 불필요.

---

### 항목 8 — MyBatis Compatibility 분기 ✅ 전체 구현 완료 (2026-05-21)

**구현된 항목**:
- Boot: mybatis-spring-boot-starter 2.3.2(4.3) / 3.0.3(5.0) — 분기됨 (기존)
- Boot: mybatis-spring-boot-starter-test 동일 버전 — 분기됨 (기존)
- WAR: mybatis-spring 버전 분기 — **신규 구현**

```java
// WAR — MYBATIS_SPRING_2 / MYBATIS_SPRING_3 상수 + supportsMyBatisSpring3() capability
String mybatisSpringVer = supportsMyBatisSpring3(s.egovVersion)
    ? MYBATIS_SPRING_3   // "3.0.3" — Spring 6 완전 지원 (eGovFrame 5.0)
    : MYBATIS_SPRING_2;  // "2.1.2" — Spring 5.x (eGovFrame 4.3)
```

Mapper XML namespace·typeAlias는 MyBatis Core 레벨 변경이 없어 영향 없음.

**판정**: 추가 구현 불필요.

---

### 항목 9 — Build Tool 표준화 ✅ 이미 구현됨

**현재 상태**:

```xml
<!-- Maven — maven-compiler-plugin 분기됨 -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.13.0</version>
    <configuration>
        <release>${java.version}</release>  <!-- 4.3→11, 5.0→17 -->
    </configuration>
</plugin>
```

```groovy
// Gradle — toolchain 분기됨
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(%s)  // 4.3→11, 5.0→17
    }
}
```

**판정**: 추가 구현 불필요.

---

### 항목 10 — Capability Matrix 기반 설계 ✅ 구현 완료 (2026-05-21)

**구현 내용**:

```java
// Spec 레코드: boolean isLatest → String egovVersion
private record Spec(boolean isBoot, String egovVersion, ...)

// compareVersion() — 시맨틱 버전 비교 ("latest" → EGOV_50 해석)
private static int compareVersion(String version, String threshold) { ... }

// 8개 capability 메서드
private static boolean supportsJakarta(String v)          { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsSpring6(String v)          { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsBoot3(String v)            { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsJava17(String v)           { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsHyphenArtifactId(String v) { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsMyBatisSpring3(String v)   { return compareVersion(v, "5.0") >= 0; }
private static String  resolveEgovVersion(String v)       { return supportsJakarta(v) ? EGOV_50 : EGOV_43; }
```

`warPomXml` · `warBuildGradle` · `bootPomXml` · `bootBuildGradle` · `dispatcherServlet` · `webXml` · `buildResult`
전 메서드의 `s.isLatest` 참조를 의미에 맞는 capability 호출로 교체 완료.

eGovFrame 5.1·5.2 신규 버전 출시 시 해당 capability 메서드 하나만 수정하면 되고
다른 분기에 영향 없는 구조 완성.

**판정**: 추가 구현 불필요.

---

## 종합 판정표

| 항목 | 최초 상태 | 최종 상태 | 위치 |
|---|---|---|---|
| 1. Java 버전 분기 | ✅ 구현됨 | ✅ 유지 | ProjectInitializrService |
| 2. javax→jakarta Java import | ❌ 미구현 | ✅ 영향 없음 확인 | CodeTemplateTool — Controller가 @ModelAttribute 사용, javax import 없음 |
| 3. Tomcat 버전 분기 | ⚠️ 암묵적 처리 | ✅ 유지 | 가이드 보강으로 충분 |
| 4. Spring Security 방식 | ⚠️ 별도 Tool | ✅ buildResult() 안내 추가 | SecurityTemplateTool + buildResult() 권장 안내 |
| 5. JSTL 의존성 | ✅ 구현됨 | ✅ 유지 | ProjectInitializrService |
| 5. JSP taglib (깨진 taglib) | ❌ 미구현 | ✅ 구현 완료 | CodeTemplateTool — spring-modules-validation taglib 제거 |
| 6. Validation API | ❌ 미구현 | ✅ 구현 완료 | ProjectInitializrService — WAR javax/jakarta 분기, Boot starter-validation |
| 7. Logging framework | ✅ 구현됨 | ✅ 유지 | ProjectInitializrService |
| 8. MyBatis WAR 버전 분기 | ⚠️ hardcoded | ✅ 구현 완료 | ProjectInitializrService — MYBATIS_SPRING_2/3 + supportsMyBatisSpring3() |
| 9. Build tool 표준화 | ✅ 구현됨 | ✅ 유지 | ProjectInitializrService |
| 10. Capability Matrix 설계 | ❌ 미구현 | ✅ 구현 완료 | ProjectInitializrService — isLatest 전면 제거, 8개 capability 메서드 |

---

## 구현 완료 이력 (2026-05-21)

| 구분 | 항목 | 완료 내용 |
|---|---|---|
| 즉시 | javax→jakarta Java import | 영향 없음 확인 (Controller 템플릿에 javax import 없음) |
| 즉시 | JSP taglib (깨진 taglib 제거) | jspRegistTemplate() — spring-modules-validation taglib URI 삭제 |
| 즉시 | Validation API 의존성 | WAR javax/jakarta 분기 + Boot starter-validation 추가 |
| 단기 | MyBatis WAR 버전 분기 | MYBATIS_SPRING_2/3 상수 + supportsMyBatisSpring3() capability |
| 단기 | buildResult() Security 안내 | 다음 단계 2번 — 권장 securityType 자동 선택 안내 삽입 |
| 중기 | Capability Matrix 설계 | isLatest 전면 제거, Spec String egovVersion, 8개 capability 메서드 |

---

## 핵심 인사이트 (최종)

**`ProjectInitializrService`가 생성하는 파일 범위에서의 모든 분기가 구현 완료됐습니다.**

최초 분석에서 Gap으로 지목된 **CRUD 소스 생성 경로(`CodeTemplateTool`)** 이슈는:
- javax→jakarta Java import: **실제 확인 결과 영향 없음** — Controller 템플릿이 `@ModelAttribute`/`ModelMap` 사용
- JSP taglib namespace: **깨진 taglib URI 제거 완료** — spring-modules-validation은 Spring 5+ 미지원

`initializeProject()`와 CRUD 소스 생성 간 `egovVersion` 미공유 구조 이슈는 현재 실질적 영향이 없으나,
향후 Controller에 `HttpServletRequest`를 직접 사용하는 패턴이 추가될 경우
`GenerationHistory` 또는 세션 컨텍스트에 `egovVersion`을 저장하고
`CodeTemplateTool`이 참조하는 구조를 도입해야 함.
