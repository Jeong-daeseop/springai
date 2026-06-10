# eGovFrame Project Initializr — 기능 정리

작성일: 2026-05-18

---

## 개요

`initializeProject` MCP Tool은 Spring Initializr처럼 eGovFrame 신규 프로젝트 골격을
파라미터 입력 한 번으로 전체 생성합니다.

---

## 파라미터

| 파라미터    | 설명                          | 예시                          |
|-------------|-------------------------------|-------------------------------|
| projectName | 프로젝트 폴더명               | egov-myproject                |
| groupId     | Maven groupId                 | kr.go.myorg                   |
| artifactId  | Maven artifactId              | myproject                     |
| packageName | 기본 Java 패키지명            | egovframework.let.myproject   |
| buildTool   | maven 또는 gradle             | gradle                        |
| projectType | war 또는 boot                 | boot                          |
| egovVersion | 4.3 또는 latest               | latest                        |
| outputPath  | 프로젝트를 생성할 상위 경로   | /Users/user/Desktop           |

---

## projectType 상세

### war — 전통 eGovFrame WAR 배포 방식

- web.xml + context-*.xml + dispatcher-servlet.xml 기반 XML 설정
- 외부 Tomcat에 WAR 파일 배포
- eGovFrame 표준 레이어 구조 그대로 적용

### boot — Spring Boot 기반 eGovFrame

- application.yml + @SpringBootApplication 구성
- 내장 서버(jar) 실행
- mybatis-spring-boot-starter 자동 구성
- @SpringBootApplication 메인 클래스 + 테스트 클래스 자동 생성

---

## egovVersion 상세

### 4.3 — eGovFrame 4.3.0

- eGovFrame : 4.3.0
- Spring     : 5.3.39
- Java       : 11
- Servlet    : javax.servlet 4.0 (Servlet API 4.0)
- MyBatis    : 3.5.16
- Boot Starter : mybatis-spring-boot-starter 2.3.2 (boot 타입 시)

### latest — eGovFrame 4.2.0 (LTS)

- eGovFrame  : 4.2.0
- Spring Boot: 3.2.5 (boot 타입) / Spring 5.3.39 (war 타입)
- Java       : 17
- Servlet    : Jakarta EE 10 (jakarta.servlet 6.0)
- MyBatis    : 3.5.16
- Boot Starter : mybatis-spring-boot-starter 3.0.3 (boot 타입 시)

---

## 4가지 조합 비교표

| projectType | egovVersion | Spring        | Java | Servlet API      | 설정 방식              | 빌드 실행            |
|-------------|-------------|---------------|------|------------------|------------------------|----------------------|
| war         | 4.3         | Spring 5.3.x  | 11   | javax.servlet 4.0 | XML (context-*.xml)   | mvn package / gradle build |
| war         | latest      | Spring 5.3.x  | 17   | jakarta.servlet 6.0 | XML (context-*.xml) | mvn package / gradle build |
| boot        | 4.3         | Boot 2.7.18   | 11   | —                | application.yml        | mvn spring-boot:run  |
| boot        | latest      | Boot 3.2.5    | 17   | —                | application.yml        | mvn spring-boot:run  |

---

## 생성 파일 목록

### 공통 (war / boot 모두)

```
{projectName}/
├── pom.xml 또는 build.gradle    ← eGovFrame 의존성 포함
├── settings.gradle              ← gradle 선택 시
├── gradle.properties            ← gradle 선택 시
├── .gitignore
├── src/main/java/{package}/     ← 패키지 디렉터리
├── src/main/resources/
│   └── egovframework/mapper/    ← Mapper XML 저장 경로
└── src/test/java/{package}/     ← 테스트 패키지 디렉터리
```

### war 타입 추가 생성 파일

```
src/main/resources/egovframework/spring/
├── context-common.xml           ← 컴포넌트 스캔 + MyBatis SqlSessionFactory
├── context-datasource.xml       ← HikariCP DataSource 설정
└── context-transaction.xml      ← AOP 기반 트랜잭션 설정

src/main/webapp/
├── index.jsp                    ← 진입점 (main.do로 forward)
├── resources/
│   ├── css/
│   └── js/
└── WEB-INF/
    ├── web.xml                  ← DispatcherServlet + 인코딩 필터
    ├── config/egovframework/springmvc/
    │   └── dispatcher-servlet.xml  ← ViewResolver + 파일업로드 설정
    └── jsp/egovframework/       ← JSP 저장 경로

src/main/resources/
└── log4j2.xml                   ← 콘솔 + 롤링 파일 로그 설정
```

### boot 타입 추가 생성 파일

```
src/main/resources/
├── application.yml              ← DataSource + MyBatis + 서버 설정
├── logback-spring.xml           ← 콘솔 + 롤링 파일 로그 설정
├── static/
│   ├── css/
│   └── js/
└── templates/

src/main/java/{package}/
└── {ArtifactId}Application.java ← @SpringBootApplication + @MapperScan

src/test/java/{package}/
└── {ArtifactId}ApplicationTests.java ← contextLoads() 기본 테스트
```

---

## war + 4.3 의존성 핵심

```xml
<!-- javax.servlet (Servlet 4.0) -->
<dependency>
    <groupId>javax.servlet</groupId>
    <artifactId>javax.servlet-api</artifactId>
    <version>4.0.1</version>
    <scope>provided</scope>
</dependency>

<!-- eGovFrame -->
<dependency>
    <groupId>egovframework.rte</groupId>
    <artifactId>egovframework.rte.ptl.mvc</artifactId>
    <version>4.3.0</version>
</dependency>
```

---

## war + latest 의존성 핵심

```xml
<!-- Jakarta EE 10 (Servlet 6.0) -->
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <version>6.0.0</version>
    <scope>provided</scope>
</dependency>

<!-- eGovFrame LTS -->
<dependency>
    <groupId>egovframework.rte</groupId>
    <artifactId>egovframework.rte.ptl.mvc</artifactId>
    <version>4.2.0</version>
</dependency>
```

---

## boot + 4.3 의존성 핵심

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.18</version>
</parent>

<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>2.3.2</version>
</dependency>
```

---

## boot + latest 의존성 핵심

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>

<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.3</version>
</dependency>
```

---

## 사용 후 다음 단계

1. DB 연결 정보 설정
   - war  : `src/main/resources/egovframework/spring/context-datasource.xml`
   - boot : `src/main/resources/application.yml` 의 `spring.datasource.*`

2. `buildFullCrudPrompt()` 로 CRUD 소스 생성 시작

3. 빌드 실행
   | 타입 | Maven              | Gradle              |
   |------|--------------------|---------------------|
   | war  | mvn clean package  | ./gradlew build     |
   | boot | mvn spring-boot:run | ./gradlew bootRun  |

---

## Maven Repository

eGovFrame 의존성은 공식 Maven 저장소에서 제공됩니다.

  https://maven.egovframe.go.kr/maven/

pom.xml 또는 build.gradle에 자동으로 추가됩니다.
