# eGovFrame Project Initializr 가이드

작성일: 2026-05-19
최종 업데이트: 2026-05-21 (JSTL 구현체 누락 수정 — org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1 추가, Tomcat 10 배포 검증 완료)

---

## 개요

`ProjectInitializrTool`은 Spring Initializr(start.spring.io)와 동일한 개념으로
eGovFrame 신규 프로젝트 골격을 한 번에 생성하는 MCP Tool입니다.

Claude에게 "프로젝트 생성해줘" 또는 "새 프로젝트 만들어줘"라고 요청하면
`initializeProject()` Tool이 자동 호출되어 표준 디렉터리 구조와 설정 파일을 즉시 생성합니다.

---

## Spring Initializr vs eGovFrame Initializr 비교

| Spring Initializr 항목 | eGovFrame Initializr | 비고 |
|---|---|---|
| Project (Gradle / Maven) | `buildTool`: `gradle` / `maven` | 동일 |
| Language | Java 전용 | Kotlin/Groovy 미지원 |
| Spring Boot 버전 | `egovVersion`으로 자동 매핑 | eGovFrame 버전 기준 |
| Group | `groupId` 파라미터 | 동일 |
| Artifact | `artifactId` 파라미터 | 동일 |
| Package name | `packageName` 파라미터 | 동일 |
| Packaging (Jar / War) | `projectType`: `boot`(Jar) / `war`(War) | 동일 |
| Java 버전 | egovVersion에 따라 자동 결정 | latest→17, 4.3→11 |
| Dependencies | eGovFrame 표준 의존성 고정 포함 | 자유 선택 불가 |
| 결과물 | 로컬 디렉터리 직접 생성 | zip 다운로드 아님 |

---

## 파라미터

| 파라미터 | 설명 | 예시 |
|---|---|---|
| `projectName` | 프로젝트 폴더명 | `egov-myproject` |
| `groupId` | Maven groupId | `kr.go.myorg` |
| `artifactId` | Maven artifactId | `myproject` |
| `packageName` | 기본 Java 패키지 | `egovframework.let.myproject` |
| `buildTool` | `maven` 또는 `gradle` | `maven` |
| `projectType` | `war` 또는 `boot` | `war` |
| `egovVersion` | `4.3` / `5.0` / `latest` (`5.0` = `latest` 동일) | `latest` |
| `outputPath` | 생성 상위 경로 (절대경로) | `/Users/user/Desktop` |

---

## projectType 선택 기준

### war — 전통 eGovFrame WAR 배포 방식
- `web.xml` + `context-*.xml` + `dispatcher-servlet.xml` XML 설정 구조
- 외부 Tomcat에 WAR 배포
- 기존 eGovFrame 레거시 프로젝트와 동일한 구조

### boot — Spring Boot 기반 eGovFrame
- `application.yml` + `@SpringBootApplication` 구성
- 내장 서버(Jar) 실행
- 신규 프로젝트 권장

---

## egovVersion 선택 기준

| egovVersion | eGovFrame | Spring Boot | Spring | Security | Batch | Java | Servlet |
|---|---|---|---|---|---|---|---|
| `4.3` | 4.3.0 | 2.7.18 | 5.3.37 | 5.8.13 | 4.3.10 | 11 | javax.servlet 4.0 |
| `5.0` / `latest` | 5.0.0 | 3.5.6 | 6.2.11 | 6.5.5 | 5.2.3 | 17 | Jakarta EE 10 |

---

## 지원 조합별 생성 내용

| projectType | egovVersion | Spring Boot | Spring | Java | 설정 방식 |
|---|---|---|---|---|---|
| `war` + `4.3` | 4.3.0 | — | 5.3.37 | 11 | javax.servlet 4.0 / XML |
| `war` + `5.0`/`latest` | 5.0.0 | — | 6.2.11 | 17 | Jakarta EE 10 / XML |
| `boot` + `4.3` | 4.3.0 | 2.7.18 | 5.3.37 | 11 | mybatis-spring-boot-starter 2.x |
| `boot` + `5.0`/`latest` | 5.0.0 | 3.5.6 | 6.2.11 | 17 | mybatis-spring-boot-starter 3.x |

---

## 생성 파일 목록

### WAR 타입 공통

```
{projectName}/
├── pom.xml  또는  build.gradle + settings.gradle + gradle.properties
├── .gitignore
└── src/
    ├── main/
    │   ├── java/{packagePath}/
    │   ├── resources/
    │   │   ├── egovframework/
    │   │   │   ├── mapper/
    │   │   │   └── spring/
    │   │   │       ├── context-common.xml
    │   │   │       ├── context-datasource.xml
    │   │   │       └── context-transaction.xml
    │   │   └── log4j2.xml
    │   └── webapp/
    │       ├── WEB-INF/
    │       │   ├── config/egovframework/springmvc/
    │       │   │   └── dispatcher-servlet.xml
    │       │   ├── jsp/egovframework/
    │       │   └── web.xml
    │       ├── resources/css/
    │       ├── resources/js/
    │       └── index.jsp
    └── test/
        └── java/{packagePath}/
```

### Boot 타입 공통

```
{projectName}/
├── pom.xml  또는  build.gradle + settings.gradle + gradle.properties
├── .gitignore
└── src/
    ├── main/
    │   ├── java/{packagePath}/
    │   │   └── {Domain}Application.java
    │   └── resources/
    │       ├── egovframework/mapper/
    │       ├── static/css/
    │       ├── static/js/
    │       ├── templates/
    │       ├── application.yml
    │       └── logback-spring.xml
    └── test/
        └── java/{packagePath}/
            └── {Domain}ApplicationTests.java
```

---

## 자동 포함 의존성

> ⚠️ **eGovFrame 5.0.0 artifact ID 명명 규칙 변경**
> - v4.3: `org.egovframe.rte:{module}` (점 구분, 예: `org.egovframe.rte.fdl.cmmn`)
> - v5.0: `egovframe-rte-{module}` (하이픈 구분, 예: `egovframe-rte-fdl-cmmn`)
> `egovVersion` 입력값에 따라 자동 분기됩니다.

### WAR 타입

| 모듈 | artifactId (v4.3) | artifactId (v5.0) | 역할 |
|---|---|---|---|
| MVC | `org.egovframe.rte.ptl.mvc` | `egovframe-rte-ptl-mvc` | Spring MVC 기반 eGovFrame MVC |
| DataAccess | `org.egovframe.rte.psl.dataaccess` | `egovframe-rte-psl-dataaccess` | MyBatis 데이터 접근 |
| Common | `org.egovframe.rte.fdl.cmmn` | `egovframe-rte-fdl-cmmn` | 공통 서비스 레이어 |
| Security | `org.egovframe.rte.fdl.security` | `egovframe-rte-fdl-security` | 보안 |
| mybatis | `mybatis:3.5.16` | `mybatis:3.5.16` | SQL 매핑 |
| mybatis-spring | `mybatis-spring:2.1.2` | `mybatis-spring:3.0.3` | MyBatis-Spring 연동 (Spring 5→6 호환) |
| validation-api | `javax.validation:validation-api:2.0.1.Final` | `jakarta.validation:jakarta.validation-api:3.0.2` | Bean Validation API |
| mysql-connector-j | `8.4.0` | `8.4.0` | MySQL 드라이버 |
| HikariCP | `5.1.0` | `5.1.0` | 커넥션 풀 |
| lombok | `1.18.32` | `1.18.32` | 보일러플레이트 제거 |
| servlet-api | `javax.servlet-api:4.0.1` | `jakarta.servlet-api:6.0.0` | Servlet API |
| jstl-impl | `javax.servlet:jstl:1.2` (API+구현체 통합) | `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` | JSTL 구현체 (Tomcat 10+ 필수) |
| junit-jupiter | `5.10.2` | `5.10.2` | 단위 테스트 |

### Boot 타입

| 모듈 | artifactId (v4.3) | artifactId (v5.0) | 역할 |
|---|---|---|---|
| spring-boot-starter-web | — | — | Spring MVC |
| spring-boot-starter-jdbc | — | — | JDBC |
| spring-boot-starter-aop | — | — | AOP (트랜잭션) |
| mybatis-spring-boot-starter | `2.3.2` | `3.0.3` | MyBatis 자동 구성 |
| spring-boot-starter-validation | — | — | Bean Validation (Jakarta/javax 자동 적용) |
| eGovFrame Common | `org.egovframe.rte.fdl.cmmn` | `egovframe-rte-fdl-cmmn` | eGovFrame 서비스 레이어 표준 |
| mysql-connector-j | — | — | MySQL 드라이버 |
| lombok | — | — | 보일러플레이트 제거 |
| spring-boot-starter-test | — | — | 통합 테스트 |

---

## 사용 예시

### Claude Desktop에서 요청

```
eGovFrame 프로젝트 생성해줘.
- 프로젝트명: egov-hr
- groupId: kr.go.hrorg
- artifactId: hr
- 패키지: egovframework.let.hr
- 빌드툴: gradle
- 타입: boot
- 버전: latest
- 저장 경로: /Users/jeongdaeseob/Desktop
```

### Claude가 자동 호출

```
initializeProject(
  projectName = "egov-hr",
  groupId     = "kr.go.hrorg",
  artifactId  = "hr",
  packageName = "egovframework.let.hr",
  buildTool   = "gradle",
  projectType = "boot",
  egovVersion = "latest",
  outputPath  = "/Users/jeongdaeseob/Desktop"
)
```

### 반환 결과 예시

```
=== eGovFrame 프로젝트 초기화 완료 ===

📌 경로   : /Users/jeongdaeseob/Desktop/egov-hr
📌 타입   : Spring Boot (내장 서버)
📌 버전   : 5.0 — eGovFrame 5.0.0 / Spring Boot 3.5.6 / Spring 6.2.11 / Security 6.5.5 / Java 17
📌 빌드   : gradle

✅ 생성 완료 (12개)
  📁 src/main/java/egovframework/let/hr/
  📁 src/main/resources/egovframework/mapper/
  📁 src/test/java/egovframework/let/hr/
  📁 src/main/resources/static/css/
  📁 src/main/resources/static/js/
  📁 src/main/resources/templates/
  📄 build.gradle
  📄 settings.gradle
  📄 gradle.properties
  📄 src/main/resources/application.yml
  📄 src/main/resources/logback-spring.xml
  📄 src/main/java/egovframework/let/hr/HrApplication.java
  📄 src/test/java/egovframework/let/hr/HrApplicationTests.java
  📄 .gitignore

📋 다음 단계
  1. application.yml 의 datasource URL/계정 설정
  2. SecurityTemplateTool 로 Security 설정 추가 (권장: boot → SECURITY_FILTER_CHAIN)
  3. buildFullCrudPrompt() 로 CRUD 소스 생성
  4. ./gradlew bootRun 으로 빌드/실행
```

---

## 프로젝트 생성 후 권장 작업 순서

```
1. initializeProject()       → 프로젝트 골격 생성
       |
       v
2. application.yml (또는 context-datasource.xml) DB 연결 정보 설정
       |
       v
3. SecurityTemplateTool      → Spring Security 설정 추가 (선택적)
       |                         boot + eGovFrame 5.x → SECURITY_FILTER_CHAIN 권장
       |                         boot + eGovFrame 4.x → SECURITY_FILTER_CHAIN 권장
       |                         war  + eGovFrame 5.x → SECURITY_FILTER_CHAIN 권장
       |                         war  + eGovFrame 4.x → WEB_SECURITY_CONFIGURER_ADAPTER
       v
4. buildFullCrudPrompt()     → 테이블 기반 CRUD 소스 자동 생성
       |
       v
5. validateGeneratedCodeDirectory()  → eGovFrame 표준 준수 검증
       |
       v
6. checkProjectHealth()      → 도메인 완성도 최종 점검
       |
       v
7. ./gradlew bootRun  또는  mvn spring-boot:run  으로 실행
```

---

## 버전별 WAR 설정 차이

### Multipart(파일 업로드) 리졸버

Spring 6에서 `CommonsMultipartResolver`가 제거됐습니다. `egovVersion`에 따라 자동 분기됩니다.

| 항목 | eGovFrame 4.3 (Spring 5) | eGovFrame 5.0 (Spring 6) |
|---|---|---|
| 리졸버 클래스 | `CommonsMultipartResolver` | `StandardServletMultipartResolver` |
| 설정 위치 | `dispatcher-servlet.xml` Bean 속성 | `dispatcher-servlet.xml` + `web.xml <multipart-config>` |
| 파일 크기 제한 | Bean `maxUploadSize` 속성 | `web.xml <max-file-size>` (50MB) |
| 요청 전체 크기 | — | `web.xml <max-request-size>` (100MB) |
| commons-fileupload 의존성 | 필요 (eGovFrame 전이 의존성) | 불필요 (Servlet 3.0 내장) |

**생성되는 `dispatcher-servlet.xml` 비교:**

```xml
<!-- eGovFrame 4.3 -->
<bean id="multipartResolver"
      class="org.springframework.web.multipart.commons.CommonsMultipartResolver">
    <property name="defaultEncoding" value="UTF-8"/>
    <property name="maxUploadSize"   value="52428800"/>
</bean>

<!-- eGovFrame 5.0 -->
<bean id="multipartResolver"
      class="org.springframework.web.multipart.support.StandardServletMultipartResolver"/>
```

**eGovFrame 5.0 `web.xml`에 자동 추가되는 `<multipart-config>`:**

```xml
<servlet>
    <servlet-name>dispatcher</servlet-name>
    ...
    <multipart-config>
        <max-file-size>52428800</max-file-size>       <!-- 50MB -->
        <max-request-size>104857600</max-request-size> <!-- 100MB -->
        <file-size-threshold>1048576</file-size-threshold> <!-- 1MB -->
    </multipart-config>
</servlet>
```

---

## 빌드 실행 명령어

| projectType | buildTool | 명령어 |
|---|---|---|
| boot | gradle | `./gradlew bootRun` |
| boot | maven | `mvn spring-boot:run` |
| war | gradle | `./gradlew build` |
| war | maven | `mvn clean package` |

---

## 주요 설계 포인트

`initializeProject()`는 eGovFrame 4.x → 5.x 전환 시 실무자가 수동으로 처리해야 하는
migration 포인트를 `egovVersion` 파라미터 하나로 자동 분기합니다.

### 1. eGovFrame 5.x Maven Artifact 명명 규칙 변경 대응

eGovFrame 5.0.0에서 Maven artifact ID 명명 규칙이 변경되었습니다.
Spring 공식 마이그레이션 가이드에 없는 eGovFrame 고유 변경으로, 잘못된 artifact ID 사용 시
빌드 단계에서 즉시 실패(`Could not resolve`)합니다.

| 구분 | eGovFrame 4.x | eGovFrame 5.x |
|---|---|---|
| MVC | `org.egovframe.rte.ptl.mvc` | `egovframe-rte-ptl-mvc` |
| DataAccess | `org.egovframe.rte.psl.dataaccess` | `egovframe-rte-psl-dataaccess` |
| Common | `org.egovframe.rte.fdl.cmmn` | `egovframe-rte-fdl-cmmn` |
| Security | `org.egovframe.rte.fdl.security` | `egovframe-rte-fdl-security` |

`egovVersion` 입력값에 따라 pom.xml / build.gradle에 올바른 artifact ID를 자동 적용합니다.

---

### 2. Spring 6 Multipart 처리 방식 변경 대응

Spring 6에서 `CommonsMultipartResolver` 클래스가 완전히 제거되었습니다.
대체 클래스인 `StandardServletMultipartResolver`는 설정 방식이 달라
**dispatcher-servlet.xml + web.xml 2개 파일을 동시에 수정**해야 합니다.

| 항목 | Spring 5.x (eGovFrame 4.x) | Spring 6.x (eGovFrame 5.x) |
|---|---|---|
| 리졸버 클래스 | `CommonsMultipartResolver` | `StandardServletMultipartResolver` |
| 파일 크기 제한 위치 | dispatcher-servlet.xml Bean 속성 | web.xml `<multipart-config>` |
| commons-fileupload 의존성 | 필요 | 불필요 (Servlet 3.0 내장) |

**dispatcher-servlet.xml 생성 내용 비교:**

```xml
<!-- eGovFrame 4.x (Spring 5) -->
<bean id="multipartResolver"
      class="org.springframework.web.multipart.commons.CommonsMultipartResolver">
    <property name="defaultEncoding" value="UTF-8"/>
    <property name="maxUploadSize"   value="52428800"/>
</bean>

<!-- eGovFrame 5.x (Spring 6) -->
<bean id="multipartResolver"
      class="org.springframework.web.multipart.support.StandardServletMultipartResolver"/>
```

eGovFrame 5.x 선택 시 web.xml에 `<multipart-config>` 블록이 자동 추가됩니다.

---

### 3. Jakarta EE 전환 대응

eGovFrame 5.x는 Jakarta EE 10 기반으로, javax 네임스페이스가 jakarta로 전환됩니다.
`egovVersion`에 따라 web.xml namespace와 servlet API 의존성을 자동 분기합니다.

| 항목 | eGovFrame 4.x (javax) | eGovFrame 5.x (jakarta) |
|---|---|---|
| web.xml xmlns | `http://xmlns.jcp.org/xml/ns/javaee` | `https://jakarta.ee/xml/ns/jakartaee` |
| web.xml version | `4.0` | `6.0` |
| servlet-api groupId | `javax.servlet` | `jakarta.servlet` |
| servlet-api version | `4.0.1` | `6.0.0` |
| JSP API | `javax.servlet.jsp-api 2.3.3` | `jakarta.servlet.jsp-api 3.1.1` |
| JSTL | `javax.servlet:jstl 1.2` (API+구현체) | `jakarta.servlet.jsp.jstl-api 3.0.0` (API) + `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` (구현체) |

> **참고**: `initializeProject()`가 생성하는 Controller 템플릿은 `@ModelAttribute` / `ModelMap` 방식을
> 사용하므로 `javax.servlet.*` import가 없습니다. javax → jakarta import 전환 이슈는 없습니다.
> 향후 `HttpServletRequest`를 직접 사용하는 패턴이 추가될 경우 별도 확인이 필요합니다.

---

### 4. Runtime Compatibility 분기 처리 — Capability Matrix 설계

`egovVersion` 파라미터 하나가 위 1·2·3번 포인트를 포함한 모든 버전 분기를 제어합니다.
내부적으로 단일 `isLatest` boolean 대신 **8개 독립 capability 메서드**로 각 분기점을 제어합니다.

| capability 메서드 | 역할 | 임계값 |
|---|---|---|
| `supportsJakarta(v)` | javax → jakarta 전환 여부 | `5.0` 이상 |
| `supportsSpring6(v)` | Spring 6 기능 사용 여부 | `5.0` 이상 |
| `supportsBoot3(v)` | Spring Boot 3.x 사용 여부 | `5.0` 이상 |
| `supportsJava17(v)` | Java 17 툴체인 사용 여부 | `5.0` 이상 |
| `supportsHyphenArtifactId(v)` | eGovFrame artifact ID 하이픈 명명 | `5.0` 이상 |
| `supportsMyBatisSpring3(v)` | mybatis-spring 3.x 사용 여부 | `5.0` 이상 |
| `resolveEgovVersion(v)` | "latest" 등을 정규화된 버전으로 변환 | — |
| `compareVersion(v, threshold)` | 시맨틱 버전 비교 ("latest" 자동 해석) | — |

eGovFrame 5.1·5.2 등 신규 버전 출시 시 해당 capability 메서드 하나만 수정하면 되며,
다른 분기에 영향이 없는 구조입니다.

| 계열 | egovVersion | Tomcat | Spring | Java | Servlet |
|---|---|---|---|---|---|
| javax 계열 | `4.3` | Tomcat 9 이하 | 5.3.37 | 11 | javax.servlet 4.0 |
| jakarta 계열 | `5.0` / `latest` | Tomcat 10+ | 6.2.11 | 17 | jakarta.servlet 6.0 |

Tomcat 버전 호환성은 egovVersion 선택의 결과로 자동 결정됩니다.

- `egovVersion=4.3` → javax 계열 의존성 + web.xml 4.0 스키마 → **Tomcat 9 이하에 배포**
- `egovVersion=5.0` / `latest` → jakarta 계열 의존성 + web.xml 6.0 스키마 → **Tomcat 10+ 필수**

> ⚠️ Tomcat 버전 불일치 시 배포 실패: Tomcat 9에 jakarta 기반 WAR 배포 불가,
> Tomcat 10+에 javax 기반 WAR 배포 불가.
