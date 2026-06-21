# eGovFrame 5.0 Manual Project Workflow

## 1. 목적

이 문서는 IDE에 종속되지 않고 Maven 또는 Gradle 기반으로 eGovFrame 5.0 레거시 Spring MVC 프로젝트를 수동 구성하는 흐름을 설명합니다.

`vscode-initializr`를 사용할 경우에는 프로젝트 템플릿 생성, POM 치환, 설정 파일 생성, CRUD 코드 생성을 조합해 이 workflow를 보조할 수 있습니다.

## 2. 기본 프로젝트 구조

Maven 기반 eGovFrame Web 프로젝트의 기본 구조는 다음과 같습니다.

```text
my-egov-project/
├── pom.xml
├── src/main/java/
│   └── egovframework/example/
│       ├── web/
│       ├── service/
│       └── service/impl/
├── src/main/resources/
│   ├── mapper/
│   ├── egovframework/
│   └── log4j2.xml
└── src/main/webapp/
    └── WEB-INF/
        ├── web.xml
        ├── spring/
        │   ├── root-context.xml
        │   └── appServlet/
        │       └── servlet-context.xml
        └── jsp/
```

Gradle도 가능하지만, eGovFrame 레거시 Web 템플릿은 Maven 중심으로 구성하는 편이 일반적입니다.

## 3. 의존성 구성

`pom.xml`에 eGovFrame 5.0 런타임과 Spring MVC 관련 의존성을 추가합니다.

주요 의존성 축:

```text
egovframe-rte-ptl-mvc
egovframe-rte-fdl-property
egovframe-rte-psl-dataaccess
egovframe-rte-fdl-idgnr
egovframe-rte-fdl-logging
spring-context
spring-webmvc
mybatis
mybatis-spring
DB driver
```

예시:

```xml
<properties>
    <egovframe.rte.version>5.0.0</egovframe.rte.version>
</properties>
```

실제 Spring, Servlet, MyBatis 버전은 eGovFrame 5.0 템플릿 POM을 기준으로 맞추는 것이 안전합니다.

## 4. `web.xml` 구성

`web.xml`은 애플리케이션 시작 시 Spring 설정 파일 위치를 알려주고, 모든 웹 요청을 `DispatcherServlet`으로 위임합니다.

주요 구성 요소:

```text
ContextLoaderListener
DispatcherServlet
CharacterEncodingFilter
contextConfigLocation
```

예시:

```xml
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>/WEB-INF/spring/root-context.xml</param-value>
</context-param>

<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>

<servlet>
    <servlet-name>appServlet</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>/WEB-INF/spring/appServlet/servlet-context.xml</param-value>
    </init-param>
</servlet>

<servlet-mapping>
    <servlet-name>appServlet</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

## 5. `servlet-context.xml` 구성

`servlet-context.xml`은 웹 계층 설정을 담당합니다.

포함할 항목:

```text
@Controller component scan
mvc:annotation-driven
ViewResolver
static resources
Interceptor
file upload resolver
```

예시:

```xml
<context:component-scan base-package="egovframework.example.web" />

<mvc:annotation-driven />

<bean class="org.springframework.web.servlet.view.InternalResourceViewResolver">
    <property name="prefix" value="/WEB-INF/jsp/" />
    <property name="suffix" value=".jsp" />
</bean>
```

## 6. `root-context.xml` 구성

`root-context.xml`은 비즈니스 로직과 인프라 계층 설정을 담당합니다.

포함할 항목:

```text
@Service, @Repository component scan
DataSource
SqlSessionFactory
MapperScannerConfigurer
TransactionManager
EgovPropertyService
EgovIdGnrService
```

이 파일에서 DB 연결, MyBatis, Transaction, Property, ID Generation 같은 eGovFrame 기반 설정을 연결합니다.

## 7. eGovFrame 5.0 표준 컴포넌트

수동 구성 시 다음 컴포넌트를 활용하면 eGovFrame 스타일의 구조를 유지할 수 있습니다.

```text
EgovAbstractServiceImpl
EgovPropertyService
EgovIdGnrService
EgovMap
PaginationInfo
Mapper interface
MyBatis XML mapper
```

CRUD 기본 구성:

```text
BoardController
BoardService
BoardServiceImpl extends EgovAbstractServiceImpl
BoardMapper
BoardVO
BoardDefaultVO
Board_SQL.xml
```

## 8. MyBatis Mapper 구성

Mapper XML 예시:

```xml
<mapper namespace="egovframework.example.service.impl.BoardMapper">
    <select id="selectBoardList" resultType="egovMap">
        SELECT *
        FROM BOARD
    </select>
</mapper>
```

`root-context.xml`에서 mapper XML 위치와 mapper interface package를 함께 연결해야 합니다.

## 9. 실행 검증 순서

```text
1. mvn clean package
2. WAS 또는 embedded container에 배포
3. Controller URL 호출
4. ViewResolver가 JSP를 찾는지 확인
5. Service, Mapper, DataSource 연결 확인
6. Transaction 동작 확인
7. PropertyService와 ID Generation 동작 확인
```

예시 URL:

```text
/board/boardList.do
```

