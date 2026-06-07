# eGovFrame Parent POM 구현 사전 영향 평가

작성일: 2026-05-23
목적: WAR 5.0 / Boot 5.0 Parent POM 전환 구현 착수 전 변경 범위·위험 요소·구현 순서 확정

---

## 변경 대상 파일

| 파일 | 메서드 | 변경 규모 |
|---|---|---|
| `ProjectInitializrService.java` | `warPomXml()` | 대 — 5.0 분기 전면 재작성 |
| `ProjectInitializrService.java` | `bootPomXml()` | 중 — parent 교체 + 버전 제거 |
| `ProjectInitializrService.java` | `warBuildGradle()` | 소 또는 없음 — Gradle은 Maven parent 미적용 |
| `ProjectInitializrService.java` | `bootBuildGradle()` | 없음 — 현행 유지 |
| `ProjectInitializrService.java` | 상수 블록 | 소 — 신규 상수 2개 추가 |

---

## 위험 1 — 4.3 WAR 회귀 (가장 큰 위험)

**eGovFrame 4.3 WAR에는 전용 Parent POM이 존재하지 않는다.**

현재 `warPomXml()`은 4.3 / 5.0을 하나의 메서드에서 조건 분기로 처리한다.

```
warPomXml(Spec s)
  ├─ egovVer         ← 4.3: "4.3.0"  / 5.0: "5.0.0"
  ├─ springVer       ← 4.3: "5.3.37" / 5.0: "6.2.11"
  ├─ mybatisSpringVer← 4.3: "2.1.2"  / 5.0: "3.0.3"
  ├─ egovDeps        ← 4.3: 점(.) / 5.0: 하이픈(-)
  ├─ servletDep      ← 4.3: javax / 5.0: jakarta
  └─ validationDep   ← 4.3: javax.validation / 5.0: jakarta.validation
```

5.0에 Parent POM 추가 시, 4.3 경로는 현행 수동 버전 관리 방식을 **그대로 유지해야 한다.**
잘못 건드리면 4.3 WAR 프로젝트 생성 즉시 build 실패.

→ **격리 방안**: `supportsEgovWebParent(egovVersion)` capability 메서드 추가 (현재 패턴과 동일),
5.0 경로만 Parent POM 방식으로 분기.

---

## 위험 2 — Parent가 관리하지 않는 의존성 버전 공백

`egovframe-web-config-parent`가 실제로 관리하는 의존성 목록이 100% 확인되지 않았다.

현재 `warPomXml()` 5.0이 수동 명시하는 항목:

| 의존성 | Parent 관리 여부 | 위험 |
|---|---|---|
| `egovframe-rte-ptl-mvc` 외 3개 | ✅ 확실 (eGov RTE = Parent 핵심) | 없음 |
| `mybatis`, `mybatis-spring` | ✅ 높음 | 낮음 |
| `mysql-connector-j` | 🔶 불확실 | 버전 없으면 컴파일 실패 |
| `HikariCP` | 🔶 불확실 | 버전 없으면 컴파일 실패 |
| `jakarta.servlet-api` | ✅ 높음 (Parent 핵심) | 낮음 |
| `jakarta.validation-api` | ✅ 높음 | 낮음 |
| `hibernate-validator` | 🔶 불확실 | 버전 없으면 runtime 실패 |
| `jakarta.el` | 🔶 불확실 | 버전 없으면 runtime 실패 |
| `lombok` | 🔶 불확실 | 버전 없으면 컴파일 실패 |
| `junit-jupiter`, `spring-test` | ✅ 높음 (Spring Parent 계승) | 낮음 |

→ **안전 전략**: 불확실 항목은 버전을 유지하되 주석으로 표시.
Parent 관리가 확인된 항목만 버전 제거. **버전을 남기는 것은 중복이지만 오류가 아니다 — Parent 값이 우선한다.**

---

## 위험 3 — `bootPomXml()` eGov RTE 버전 공백

Boot 5.0에서 `org.egovframe.boot.starter.parent`로 교체 시, 현재 `${egov.version}`으로 명시되어 있는
eGovFrame RTE 의존성의 버전 관리 주체가 Parent로 이동한다.

```java
// 현재
<dependency>
    <groupId>org.egovframe.rte</groupId>
    <artifactId>egovframe-rte-fdl-cmmn</artifactId>
    <version>${egov.version}</version>  ← 제거 대상
</dependency>
```

Parent가 관리하지 않는 경우 `<version>` 제거 시 **빌드 실패**.

→ **안전 전략**: 첫 구현에서는 `<version>` 태그를 주석 처리 형태로 남기거나 유지.
실제 동작 확인 후 정리.

---

## 위험 4 — `warBuildGradle()` 처리 방향

Maven `<parent>` 상속은 Maven 전용 개념이다. Gradle은 적용 불가.

Gradle에서 BOM 효과를 얻으려면:
```groovy
dependencies {
    implementation platform('org.egovframe.web:egovframe-web-config-parent:5.0.0')
}
```
그러나 `egovframe-web-config-parent`가 Gradle `platform()` import를 지원하는지 미확인.

→ **결론: `warBuildGradle()`은 이번 구현에서 변경 제외.**
수동 버전 관리 방식 유지. 별도 확인 후 진행.

---

## 위험 5 — 상수 사용 범위 축소

Parent POM 도입 후 5.0 경로에서 일부 상수가 사용되지 않게 된다.

| 상수 | 현재 사용처 | 5.0 Parent 도입 후 |
|---|---|---|
| `SPRING_6` | `warPomXml()` 5.0 `<spring.version>` | 5.0 WAR에서 불필요 (단, 4.3 WAR 유지) |
| `SPRING_BOOT_3` | `bootPomXml()` parent version | Boot 5.0 parent version으로 `EGOV_50` 사용 |
| `MYBATIS_SPRING_3` | `warPomXml()` 5.0 | 5.0 WAR에서 불필요 (단, 4.3 유지) |
| `MYBATIS_35` | `warBuildGradle()` | Gradle 미변경 시 유지 |

→ 상수 삭제 시 4.3 경로 파괴 위험. **이번 구현에서 상수 삭제 금지, 신규 상수만 추가.**

---

## 구현 순서 (의존성 기반)

```
[1단계] 상수 추가 (부작용 없음)
        EGOV_WAR_PARENT_GROUP    = "org.egovframe.web"
        EGOV_WAR_PARENT_ARTIFACT = "egovframe-web-config-parent"
        EGOV_BOOT_PARENT_GROUP   = "org.egovframe.boot"
        EGOV_BOOT_PARENT_ARTIFACT= "org.egovframe.boot.starter.parent"
        supportsEgovParent(v)    ← supportsJakarta()와 동일 조건 (5.0 이상)
        ↓

[2단계] bootPomXml() 수정 (범위 작음, 리스크 낮음)
        5.0: parent groupId/artifactId → EGOV_BOOT_PARENT
        5.0: parent version → egovVer (EGOV_50 = "5.0.0")
        5.0: mybatis-spring-boot-starter 버전 유지 (안전 전략)
        5.0: spring-boot-maven-plugin 버전 명시 → 제거 (parent pluginManagement)
        4.3: 현행 spring-boot-starter-parent 그대로
        ↓

[3단계] warPomXml() 수정 (범위 가장 큼)
        5.0: <parent> 블록 추가
        5.0: <properties>에서 spring.version, mybatis.version 제거
        5.0: eGov RTE 의존성 <version> 제거 (parent BOM 관리)
        5.0: maven-compiler-plugin, maven-war-plugin 제거 (parent pluginManagement)
        5.0: 불확실 항목(HikariCP, mysql, hibernate-validator 등) 버전 유지
        4.3: 현행 수동 버전 관리 전혀 건드리지 않음
        ↓

[4단계] bootJar 빌드 확인
        컴파일 오류 없으면 완료
        (런타임 검증은 실제 eGovFrame Maven repo 접근 후 가능)
```

---

## 변경 영향 범위

```
ProjectInitializrService 변경
  ├─ initializeProject()           ← 자동 반영 (warPomXml/bootPomXml 호출자)
  ├─ getConfigTemplate()           ← 영향 없음 (dispatcherServlet 등 별도 메서드)
  ├─ warBuildGradle()              ← 이번 구현 제외
  └─ bootBuildGradle()             ← 이번 구현 제외

CodeTemplateTool / CrudPromptBuilderService 등 ← 전혀 영향 없음
```

---

## 비파괴성 검토

| 항목 | 기존 생성 프로젝트 영향 | 이유 |
|---|---|---|
| `warPomXml()` 변경 | **없음** | 이미 생성된 pom.xml 불변 |
| `bootPomXml()` 변경 | **없음** | 신규 `initializeProject()` 호출에만 적용 |
| 4.3 생성 프로젝트 | **없음** | 4.3 경로 미변경 |

---

## 구현 후 검증 항목

| 검증 | 방법 | 결과 |
|---|---|---|
| 4.3 WAR pom.xml 동일 출력 | `getConfigTemplate()` 또는 `initializeProject()` 호출 | ✅ 4.3 경로 미변경 확인 |
| 4.3 Boot pom.xml 동일 출력 | 동일 | ✅ 4.3 경로 미변경 확인 |
| 5.0 WAR pom.xml parent 변경 | 생성 pom.xml 확인 | ✅ `egovframe-web-config-parent` 포함 |
| 5.0 Boot pom.xml parent 변경 | 생성 pom.xml 확인 | ✅ `org.egovframe.boot.starter.parent` 포함 |
| bootJar 빌드 성공 | `./gradlew bootJar` | ✅ BUILD SUCCESSFUL (2026-05-23) |
| 런타임 의존성 해소 (선택) | 생성된 프로젝트에서 `mvn dependency:tree` | 🔶 실제 eGovFrame Maven repo 접근 후 가능 |

---

## 최종 결정 사항

| 항목 | 결정 | 완료 |
|---|---|---|
| WAR 5.0 parent 교체 | ✅ **구현** | ✅ 2026-05-23 완료 |
| WAR 5.0 확실한 버전 제거 | ✅ **구현** (eGov RTE, Spring, MyBatis, Servlet API) | ✅ 2026-05-23 완료 |
| WAR 5.0 불확실 항목 버전 | 🔶 **유지** (HikariCP, mysql, hibernate-validator, lombok, jakarta.el) | ✅ 유지 확인 |
| Boot 5.0 parent 교체 | ✅ **구현** | ✅ 2026-05-23 완료 |
| Boot 5.0 egov.version property | ✅ **제거** (parent BOM 관리) | ✅ 2026-05-23 완료 |
| Boot 5.0 eGov RTE `<version>` | ✅ **제거** (parent BOM 관리) | ✅ 2026-05-23 완료 |
| Boot 5.0 MyBatis 버전 | 🔶 **유지** (parent 관리 확인 후 제거) | ✅ 유지 확인 |
| WAR build.gradle | ❌ **이번 제외** (Gradle platform import 미확인) | — |
| Boot build.gradle | ❌ **변경 없음** | — |
| 4.3 경로 일체 | ❌ **변경 없음** | ✅ 미변경 확인 |
| 상수 삭제 | ❌ **이번 제외** | — |
