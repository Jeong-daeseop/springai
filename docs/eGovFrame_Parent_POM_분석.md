# eGovFrame Parent POM 구조 분석

작성일: 2026-05-23
목적: WAR / Boot 프로젝트 유형별 eGovFrame Parent POM 좌표 확인 및 generator 반영 범위 확정

---

## 1. 최종 확인된 Parent POM 좌표

| 유형 | groupId | artifactId | version |
|---|---|---|---|
| Boot 5.0 | `org.egovframe.boot` | `org.egovframe.boot.starter.parent` | `5.0.0` |
| WAR 5.0 | `org.egovframe.web` | `egovframe-web-config-parent` | `5.0.0` |
| Boot 4.3 | ❓ eGov 전용 Parent 미확인 → `spring-boot-starter-parent` 유지 | | |
| WAR 4.3 | ❓ eGov 전용 Parent 미확인 → 수동 버전 관리 유지 | | |

---

## 2. 프로젝트 유형별 Parent POM 역할

### Boot 5.0 — `org.egovframe.boot.starter.parent`

```
org.egovframe.boot.starter.parent
 ├─ spring-boot-starter-parent     ← Spring Boot Parent 상속
 ├─ eGov BOM                       ← eGov RTE 버전 통합
 ├─ pluginManagement               ← spring-boot-maven-plugin 등
 ├─ dependencyManagement           ← MyBatis, Security, JSTL 등
 ├─ Java 17 settings
 ├─ Jakarta ecosystem 표준화
 └─ executable JAR 생태계
```

**특징:**
- Spring Boot Embedded Tomcat 환경
- starter 자동 구성(auto-configuration)
- executable JAR

### WAR 5.0 — `egovframe-web-config-parent`

```
egovframe-web-config-parent
 ├─ dependencyManagement           ← Spring 6, Security 6, RTE 5.0, MyBatis, JSTL, Commons
 ├─ pluginManagement               ← maven-compiler-plugin, maven-war-plugin 등
 ├─ Java 17 settings
 ├─ Spring 6 + Jakarta 전환 반영
 └─ External Tomcat WAR 생태계
```

**특징:**
- External Tomcat 10+ 배포 환경 (WAR)
- JSP / JSTL / web.xml 지원
- Jakarta Servlet API

---

## 3. BOM vs Parent POM 비교

| 항목 | eGov BOM (import) | Parent POM (`<parent>`) |
|---|---|---|
| dependency 버전 관리 | ✅ | ✅ |
| plugin 버전 관리 | ❌ | ✅ |
| Java version 설정 | ❌ | ✅ |
| build lifecycle 설정 | ❌ | ✅ |
| executable JAR 지원 | ❌ | ✅ |
| 사용 방식 | `<dependencyManagement>` import | `<parent>` 상속 |

**결론:** eGovFrame 5.0은 단순 BOM import 구조가 아닌 **Parent POM 기반 호환성 플랫폼** 구조를 채택한다.

---

## 4. 비유 정리

| 대상 | 비유 |
|---|---|
| eGov BOM | "부품 규격표" — 라이브러리 버전만 통합 |
| `org.egovframe.boot.starter.parent` | "조립 공장 + 규격표 + 생산라인" — 프로젝트 전체 개발 표준화 |
| `egovframe-web-config-parent` | "WAR 조립 공장 + 규격표" — External Tomcat WAR 표준화 |

---

## 5. 왜 중요한가 — dependency mismatch 위험

Spring Boot 3 + Jakarta 환경에서 버전 혼재 시 **runtime crash** 발생:

```
Spring 6
+ Security 5      ← 버전 불일치
+ javax JSTL      ← namespace 불일치
= runtime crash 가능
```

Parent POM이 호환 버전 세트를 공식 플랫폼으로 일괄 관리함으로써 이 위험을 제거한다.

---

## 6. 현재 Generator Gap 분석

### WAR 5.0 — `warPomXml()` 현재 vs 변경 후

| 항목 | 현재 (수동 관리) | 변경 후 (parent 교체) |
|---|---|---|
| `<parent>` | **없음** | `egovframe-web-config-parent:5.0.0` 추가 |
| `<spring.version>` property | 수동 명시 | **제거** (parent 관리) |
| `<mybatis.version>` property | 수동 명시 | **제거** (parent 관리) |
| `<egov.version>` property | 수동 명시 | **제거** (parent 관리) |
| eGov RTE 의존성 `<version>` | `${egov.version}` 명시 | **제거** (parent BOM 관리) |
| Spring, MyBatis 의존성 `<version>` | 수동 명시 | **제거** (parent 관리) |
| `maven-compiler-plugin` 선언 | 수동 명시 | **제거** (parent pluginManagement 관리) |
| `maven-war-plugin` 선언 | 수동 명시 | **제거** (parent pluginManagement 관리) |

### Boot 5.0 — `bootPomXml()` 현재 vs 변경 후

| 항목 | 현재 | 변경 후 |
|---|---|---|
| `<parent>` | `spring-boot-starter-parent:3.5.6` | `org.egovframe.boot.starter.parent:5.0.0` |
| `<egov.version>` property | 수동 명시 | **제거** (parent 관리) |
| `mybatis-spring-boot-starter` 버전 | 수동 명시 | **제거** (parent 관리) |
| `spring-boot-maven-plugin` 버전 | parent가 관리 (이미 버전 없음) | 유지 (lombok exclude 설정 유지) |

### Boot build.gradle — 변경 불필요

Maven `<parent>` 상속은 Maven 전용 개념이다. Gradle은 `org.springframework.boot` plugin 직접 선언 방식을 유지한다.
eGovFrame Boot BOM이 Gradle용으로 별도 제공된다면 `platform()` 또는 `mavenBom()` import로 추가 가능하나, 현재 확인되지 않아 유지.

---

## 7. 구현 준비 상태

| 항목 | 상태 | 비고 |
|---|---|---|
| WAR 5.0 parent 교체 + 버전 정리 | ✅ **구현 준비 완료** | 좌표 확인됨 |
| Boot 5.0 parent 교체 + 버전 정리 | ✅ **구현 준비 완료** | 좌표 확인됨 |
| WAR 4.3 처리 | ❓ **확인 필요** | 전용 Parent 없으면 수동 버전 관리 유지 |
| Boot 4.3 처리 | ❓ **확인 필요** | 전용 Parent 없으면 `spring-boot-starter-parent` 유지 |
| Gradle Boot 처리 | 🔶 **유지** | Maven Parent ≠ Gradle, 별도 확인 필요 |

---

## 8. 구현 시 변경 대상 파일

| 파일 | 변경 내용 |
|---|---|
| `ProjectInitializrService.warPomXml()` | `<parent>` 추가, 수동 버전 properties/tags 제거 (5.0 분기) |
| `ProjectInitializrService.bootPomXml()` | `<parent>` groupId/artifactId 교체, 중복 버전 제거 (5.0 분기) |
| `ProjectInitializrService` 상수 | `EGOV_WAR_PARENT`, `EGOV_BOOT_PARENT` 상수 추가 권장 |
