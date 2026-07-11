# ProjectInitializr Tool 구현 분석

작성일: 2026-05-21
최종 업데이트: 2026-05-21 (Capability Matrix 도입·Validation API·MyBatis WAR 분기·buildResult Security 안내·warPomXml formatted() 버그 수정 반영)
대상 파일:
- `tools/ProjectInitializrTool.java`
- `service/ProjectInitializrService.java`

---

## 1. 전체 구조

```
ProjectInitializrTool (@Tool 2개)
    └── ProjectInitializrService
            ├── initializeProject()   — 프로젝트 골격 파일시스템에 직접 생성
            └── getConfigTemplate()   — 설정 파일 템플릿 문자열 단독 반환
```

Tool은 위임만 수행하고, 실제 로직은 Service에 집중됩니다.
Service 내부는 5개 레이어로 구성됩니다.

```
initializeProject()
    ├── createDirectories()   — 디렉터리 구조 생성
    ├── createBuildFile()     — pom.xml 또는 build.gradle 생성
    ├── createWarFiles()      — WAR 전용 설정 파일 생성
    ├── createBootFiles()     — Boot 전용 설정 파일 생성
    └── buildResult()         — 결과 요약 문자열 반환
```

---

## 2. Tool 파라미터

### Tool 1: `initializeProject`

| 파라미터 | 설명 | 예시 |
|---|---|---|
| `projectName` | 프로젝트 폴더명 | `egov-myproject` |
| `groupId` | Maven groupId | `kr.go.myorg` |
| `artifactId` | Maven artifactId | `myproject` |
| `packageName` | 기본 Java 패키지 | `egovframework.let.myproject` |
| `buildTool` | `maven` 또는 `gradle` | `gradle` |
| `projectType` | `war` 또는 `boot` | `boot` |
| `egovVersion` | `4.3` / `5.0` / `latest` | `latest` |
| `outputPath` | 생성 상위 경로 (절대경로) | `/Users/user/Desktop` |

> `5.0`과 `latest`는 동일하게 처리됩니다. (`compareVersion(v, "5.0") >= 0` — "latest"는 내부적으로 EGOV_50("5.0.0")으로 해석)

### Tool 2: `getConfigTemplate`

| 파라미터 | 설명 |
|---|---|
| `configType` | 반환할 설정 파일 종류 (아래 목록 참조) |
| `packageName` | 패키지명 (contextCommon·dispatcherServlet·applicationYml에서 사용, 생략 시 `egovframework.let.sample`) |

**지원 configType:**

| configType | 대상 파일 | 주요 설정 내용 |
|---|---|---|
| `contextCommon` | context-common.xml | 컴포넌트 스캔, SqlSessionFactory, MapperScanner |
| `contextDatasource` | context-datasource.xml | HikariCP DataSource (MySQL 기준) |
| `contextTransaction` | context-transaction.xml | DataSourceTransactionManager + AOP 포인트컷 |
| `dispatcherServlet` | dispatcher-servlet.xml | MVC 컨트롤러 스캔, ViewResolver, 파일 업로드 리졸버 |
| `webXml` | web.xml | ContextLoaderListener, DispatcherServlet, 인코딩 필터 |
| `logback` | logback-spring.xml | Boot 전용 콘솔 + 롤링 로그 |
| `log4j2` | log4j2.xml | WAR 전용 콘솔 + 롤링 로그 |
| `applicationYml` | application.yml | datasource / mybatis / server / local·prod 프로파일 |

---

## 3. 버전 상수 관리

Service 상단에 버전 상수를 모두 선언하여 한 곳에서 관리합니다.

```java
// eGovFrame 4.3
private static final String EGOV_43        = "4.3.0";
private static final String SPRING_5       = "5.3.37";
private static final String SPRING_BOOT_2  = "2.7.18";
private static final String SPRING_SEC_5   = "5.8.13";
private static final String SPRING_BAT_4   = "4.3.10";
private static final String MYBATIS_35     = "3.5.16";
private static final String MYBATIS_SPRING_2 = "2.1.2"; // WAR — Spring 5.x
private static final String MYBATIS_SB2    = "2.3.2";   // Spring Boot 2.x 전용

// eGovFrame 5.0
private static final String EGOV_50        = "5.0.0";
private static final String SPRING_6       = "6.2.11";
private static final String SPRING_BOOT_3  = "3.5.6";
private static final String SPRING_SEC_6   = "6.5.5";
private static final String SPRING_BAT_5   = "5.2.3";
private static final String MYBATIS_SPRING_3 = "3.0.3"; // WAR — Spring 6.x
private static final String MYBATIS_SB3    = "3.0.3";   // Spring Boot 3.x 전용

private static final String JAVA_11 = "11";
private static final String JAVA_17 = "17";

// compareVersion("latest", ...) 해석 기준
private static final String EGOV_LATEST = EGOV_50;
```

버전 분기는 **Capability Matrix** 메서드로 제어합니다. `isLatest` 단일 boolean은 제거되었습니다.

```java
// initializeProject() — Spec 생성
boolean isBoot = "boot".equalsIgnoreCase(projectType);
Spec spec = new Spec(isBoot, egovVersion, groupId, artifactId, packageName, buildTool);

// 각 메서드 내부 — 의도에 맞는 capability 메서드 호출
String javaVer   = supportsJava17(s.egovVersion)   ? JAVA_17    : JAVA_11;
String springVer = supportsSpring6(s.egovVersion)  ? SPRING_6   : SPRING_5;
String sbVer     = supportsBoot3(s.egovVersion)    ? SPRING_BOOT_3 : SPRING_BOOT_2;
```

---

## 4. 버전 매트릭스

| 구분 | eGovFrame 4.3 | eGovFrame 5.0 (latest) | capability 메서드 |
|---|---|---|---|
| eGovFrame | 4.3.0 | 5.0.0 | `resolveEgovVersion()` |
| Spring | 5.3.37 | 6.2.11 | `supportsSpring6()` |
| Spring Boot | 2.7.18 | 3.5.6 | `supportsBoot3()` |
| Spring Security | 5.8.13 | 6.5.5 | `supportsSpring6()` |
| Spring Batch | 4.3.10 | 5.2.3 | `supportsSpring6()` |
| MyBatis Core | 3.5.16 | 3.5.16 | — (공통) |
| MyBatis-Spring (WAR) | 2.1.2 | 3.0.3 | `supportsMyBatisSpring3()` |
| MyBatis SB Starter | 2.3.2 | 3.0.3 | `supportsBoot3()` |
| Java | 11 | 17 | `supportsJava17()` |
| Servlet API | `javax.servlet` 4.0 | `jakarta.servlet` 6.0 | `supportsJakarta()` |
| Validation API (WAR) | `javax.validation` 2.0.1 | `jakarta.validation` 3.0.2 | `supportsJakarta()` |
| artifactId 명명 | 점(.) 구분 | 하이픈(-) 구분 | `supportsHyphenArtifactId()` |

---

## 5. 생성 파일 분기

### 공통 디렉터리 (war·boot 공통)

```
src/main/java/{packagePath}/
src/main/resources/egovframework/mapper/
src/test/java/{packagePath}/
```

### WAR 추가 디렉터리

```
src/main/resources/egovframework/spring/
src/main/webapp/WEB-INF/config/egovframework/springmvc/
src/main/webapp/WEB-INF/jsp/egovframework/
src/main/webapp/resources/css/
src/main/webapp/resources/js/
```

### Boot 추가 디렉터리

```
src/main/resources/static/css/
src/main/resources/static/js/
src/main/resources/templates/
```

### WAR 생성 파일

| 파일 | 경로 |
|---|---|
| context-common.xml | `src/main/resources/egovframework/spring/` |
| context-datasource.xml | `src/main/resources/egovframework/spring/` |
| context-transaction.xml | `src/main/resources/egovframework/spring/` |
| dispatcher-servlet.xml | `src/main/webapp/WEB-INF/config/egovframework/springmvc/` |
| web.xml | `src/main/webapp/WEB-INF/` |
| index.jsp | `src/main/webapp/` |
| log4j2.xml | `src/main/resources/` |

### Boot 생성 파일

| 파일 | 경로 |
|---|---|
| application.yml | `src/main/resources/` |
| logback-spring.xml | `src/main/resources/` |
| `{Domain}Application.java` | `src/main/java/{packagePath}/` |
| `{Domain}ApplicationTests.java` | `src/test/java/{packagePath}/` |

---

## 6. eGovFrame 5.0 artifact ID 명명 규칙 변경 처리

eGovFrame 5.0.0에서 Maven artifact ID 명명 규칙이 변경되었습니다.
`supportsHyphenArtifactId()` capability 메서드로 분기합니다.

| 모듈 | 4.3 (점 구분) | 5.0 (하이픈 구분) |
|---|---|---|
| MVC | `org.egovframe.rte.ptl.mvc` | `egovframe-rte-ptl-mvc` |
| DataAccess | `org.egovframe.rte.psl.dataaccess` | `egovframe-rte-psl-dataaccess` |
| Common | `org.egovframe.rte.fdl.cmmn` | `egovframe-rte-fdl-cmmn` |
| Security | `org.egovframe.rte.fdl.security` | `egovframe-rte-fdl-security` |

```java
// bootPomXml() / bootBuildGradle()
String fdlCmmnId = supportsHyphenArtifactId(s.egovVersion)
    ? "egovframe-rte-fdl-cmmn"      // 5.0+
    : "org.egovframe.rte.fdl.cmmn"; // 4.3
```

---

## 7. Spring 6 파일 업로드 처리 분기

Spring 6(eGovFrame 5.0+)에서 `CommonsMultipartResolver`가 제거되었습니다.
`supportsSpring6()` capability 메서드로 리졸버 클래스와 설정 위치를 자동 분기합니다.

| 항목 | eGovFrame 4.3 (Spring 5) | eGovFrame 5.0 (Spring 6) |
|---|---|---|
| 리졸버 클래스 | `CommonsMultipartResolver` | `StandardServletMultipartResolver` |
| 설정 위치 | dispatcher-servlet.xml Bean | dispatcher-servlet.xml + web.xml `<multipart-config>` |
| 파일 크기 제한 | Bean `maxUploadSize` 속성 (52MB) | `web.xml <max-file-size>` (50MB) |
| 요청 전체 크기 | — | `web.xml <max-request-size>` (100MB) |
| commons-fileupload | 필요 | 불필요 (Servlet 3.0 내장) |

**eGovFrame 4.3 (dispatcher-servlet.xml):**
```xml
<bean id="multipartResolver"
      class="org.springframework.web.multipart.commons.CommonsMultipartResolver">
    <property name="defaultEncoding" value="UTF-8"/>
    <property name="maxUploadSize"   value="52428800"/>
</bean>
```

**eGovFrame 5.0 (dispatcher-servlet.xml):**
```xml
<bean id="multipartResolver"
      class="org.springframework.web.multipart.support.StandardServletMultipartResolver"/>
```

**eGovFrame 5.0 (web.xml — `<multipart-config>` 자동 추가):**
```xml
<servlet>
    <servlet-name>dispatcher</servlet-name>
    ...
    <multipart-config>
        <max-file-size>52428800</max-file-size>
        <max-request-size>104857600</max-request-size>
        <file-size-threshold>1048576</file-size-threshold>
    </multipart-config>
</servlet>
```

---

## 8. web.xml 네임스페이스 분기

| 항목 | eGovFrame 4.3 | eGovFrame 5.0 |
|---|---|---|
| xmlns | `http://xmlns.jcp.org/xml/ns/javaee` | `https://jakarta.ee/xml/ns/jakartaee` |
| XSD 위치 | `javaee/web-app_4_0.xsd` | `jakartaee/web-app_6_0.xsd` |
| version | `4.0` | `6.0` |

---

## 9. application.yml 주요 설계

Boot 프로젝트 생성 시 환경변수 주입과 프로파일 분리를 기본 포함합니다.

**환경변수 주입 (기본값 포함):**
```yaml
datasource:
  url: ${DB_URL:jdbc:mysql://localhost:3306/ebt?...}
  username: ${DB_USERNAME:ebt}
  password: ${DB_PASSWORD:ebt01}
  hikari:
    maximum-pool-size: ${DB_POOL_MAX:10}
```

**프로파일 분리 (`---` 구분자):**
```yaml
# 기본 (공통)
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

---
# local 프로파일
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:mysql://localhost:3306/com...

---
# prod 프로파일
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}        # 환경변수 필수
    hikari:
      maximum-pool-size: ${DB_POOL_MAX:30}
```

---

## 10. Boot 메인 클래스 생성

artifactId를 PascalCase로 변환하여 클래스명을 결정합니다.

```java
private String toPascalCase(String artifactId) {
    StringBuilder sb = new StringBuilder();
    for (String part : artifactId.split("[-_]")) {
        if (!part.isEmpty())
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
    }
    return sb.toString();
}
```

예: `egov-hr` → `EgovHr`, `my_project` → `MyProject`

생성되는 메인 클래스:
```java
@SpringBootApplication
@MapperScan("egovframework.let.myproject")
public class MyprojectApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyprojectApplication.class, args);
    }
}
```

---

## 11. 결과 출력 구조

`buildResult()`가 반환하는 문자열 구조 (`String egovVersion` 파라미터 기반):

```
=== eGovFrame 프로젝트 초기화 완료 ===

📌 경로   : /Users/user/Desktop/egov-myproject
📌 타입   : Spring Boot (내장 서버)
📌 버전   : 5.0 — eGovFrame 5.0.0 / Spring Boot 3.5.6 / Spring 6.2.11 / ...
📌 빌드   : gradle

✅ 생성 완료 (14개)
  📁 src/main/java/...
  📄 build.gradle
  ...

⚠️  오류 (1개)          ← 오류 발생 시에만 표시
  ❌ ...

📋 다음 단계
  1. application.yml 의 datasource URL/계정 설정
  2. Spring Security 설정 추가 (선택)
     → getSecurityTemplate("boot-security-filter-chain", "<packageName>", "5.0")
  3. buildFullCrudPrompt() 로 CRUD 소스 생성
  4. ./gradlew bootRun 으로 빌드/실행
```

**securityType 자동 선택 기준 (`supportsSpring6()` / `supportsBoot3()` 사용)**

| projectType | egovVersion | 권장 securityType |
|---|---|---|
| Boot | 5.0 | `boot-security-filter-chain` |
| Boot | 4.3 | `boot-security-adapter` |
| WAR | 5.0 | `java-config-filter-chain` |
| WAR | 4.3 | `xml-legacy` |

---

## 12. Tool Description 설계 포인트

```java
@Tool(description = """
    ⚠️ 이 Tool이 outputPath 경로에 프로젝트 파일을 직접 생성합니다.
    Desktop Commander, Bash, 기타 파일 생성 도구를 사용하지 마세요.
    ...
    ⚠️ 사용자가 "프로젝트 생성", "새 프로젝트 만들어줘", "프로젝트 초기화" 요청 시
       반드시 이 Tool을 직접 호출하세요. Desktop Commander나 Bash로 대체하지 마세요.
    projectType 또는 egovVersion 미입력 시 사용자에게 물어보세요.
    """)
```

핵심 설계 의도:
- **독점성 명시**: Claude가 다른 도구(Desktop Commander, Bash)로 대체하는 것을 막음
- **트리거 키워드 명시**: "프로젝트 생성", "새 프로젝트 만들어줘", "프로젝트 초기화"
- **필수 확인 항목**: `projectType`과 `egovVersion`은 미입력 시 사용자에게 반드시 물어보도록 지시

---

## 13. 잠재적 이슈 및 수정 이력

| 항목 | 위치 | 상태 | 내용 |
|---|---|---|---|
| **Service 주석 오류** | Service.java 상단 주석 | 미수정 | 주석에 `eGovFrame 4.2.0 (LTS)`로 표기되어 있으나 실제 상수는 `5.0.0` — 코드에는 영향 없음 |
| **getConfigTemplate webXml 버전** | `getConfigTemplate()` | 수정됨 | `webXml` 호출 시 문자열 `"5.0"` 전달(Jakarta EE 기준) / `dispatcherServlet` 호출 시 `"4.3"` 전달. `boolean` → `String egovVersion` 교체로 의미 명확화 |
| **warPomXml formatted() 인자 순서** | `warPomXml()` | ✅ 수정 완료 | 7번째 `%s`(`<mybatis.version>` 프로퍼티)에 `egovDeps`가 주입되던 버그 — `MYBATIS_35`와 `egovDeps` 순서 교정 |
| **MySQL 전용 고정** | contextDatasource, application.yml | 미수정 | DataSource가 MySQL 드라이버·URL 포맷 하드코딩 — 다른 DB 사용 시 수동 변경 필요 |
| **Boot pom.xml 파라미터 중복** | `bootPomXml()` | 미수정 (의도적) | `mbsbVer`가 MyBatis Starter·Test Starter 두 곳 사용됨 — 의도적이나 `.formatted()` 인자 순서 파악이 어려움 |

---

## 14. Capability Matrix 구조

`isLatest` 단일 boolean 제거 후 도입된 8개 capability 메서드. eGovFrame 5.1·5.2 신규 버전 출시 시
해당 메서드 하나만 수정하면 되고 다른 분기에 영향 없음.

```java
// 시맨틱 버전 비교 — "latest" 는 EGOV_LATEST(5.0.0)로 해석
private static int compareVersion(String version, String threshold) { ... }

// 각 런타임 특성별 독립 메서드 (현재 모두 5.0 임계값)
private static boolean supportsJakarta(String v)          { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsSpring6(String v)          { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsBoot3(String v)            { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsJava17(String v)           { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsHyphenArtifactId(String v) { return compareVersion(v, "5.0") >= 0; }
private static boolean supportsMyBatisSpring3(String v)   { return compareVersion(v, "5.0") >= 0; }
private static String  resolveEgovVersion(String v)       { return supportsJakarta(v) ? EGOV_50 : EGOV_43; }
```

**각 메서드의 사용 위치**

| capability 메서드 | 사용 위치 |
|---|---|
| `supportsJakarta` | servletDep·validationDep 분기, web.xml namespace·XSD·version, `buildResult` 버전 레이블 |
| `supportsSpring6` | multipartResolver 분기 (`dispatcherServlet`·`webXml`), `buildResult` securityType |
| `supportsBoot3` | sbVer·mbsbVer (Boot pom/gradle), `buildResult` securityType |
| `supportsJava17` | javaVer (WAR·Boot pom/gradle toolchain) |
| `supportsHyphenArtifactId` | egovDeps (WAR pom/gradle), fdlCmmnId (Boot pom/gradle) |
| `supportsMyBatisSpring3` | mybatisSpringVer (WAR pom/gradle) |
| `resolveEgovVersion` | egovVer 실제 버전 문자열 결정 (warPomXml·warBuildGradle·bootPomXml·bootBuildGradle) |

---

## 15. 전체 처리 흐름 (initializeProject)

```
Claude Desktop 요청
    │
    ▼
ProjectInitializrTool.initializeProject(8개 파라미터)
    │
    ▼
ProjectInitializrService.initializeProject()
    │
    ├─ Spec 레코드 생성 (isBoot, egovVersion, groupId, artifactId, packageName, buildTool)
    │
    ├─ createDirectories()
    │      공통 3개 + war 5개 또는 boot 3개 디렉터리 Files.createDirectories()
    │
    ├─ createBuildFile()
    │      gradle → build.gradle + settings.gradle + gradle.properties
    │      maven  → pom.xml
    │      isBoot·capability 메서드 조합으로 의존성·버전 분기
    │
    ├─ isBoot ? createBootFiles() : createWarFiles()
    │      각 파일을 writeFile()로 생성
    │      writeFile() → Files.createDirectories() + Files.writeString()
    │
    ├─ writeFile(".gitignore")
    │
    └─ buildResult() → 결과 요약 문자열 반환 → Claude Desktop 표시
```
