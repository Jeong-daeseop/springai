# CrudPromptBuilderTool 수정요구 명세서

> 기준일: 2026-06-24  
> 대상: `buildBoardFeature`, `initializeProject`  
> 목적: 생성 후 수동 보정이 필요한 항목을 생성기 자체에서 제거한다.

## 1. 배경

현재 `buildBoardFeature(viewType="thymeleaf")`로 생성한 게시판 소스는 빌드는 가능하지만, 생성 직후 검증에서 다음 항목이 미준수로 남는다.

- `BbsVO.java`의 검색/페이징 필드 불완전
- `EgovBbsServiceImpl.java`의 트랜잭션/주입 표준 미반영
- `initializeProject()` 산출물인 `index.jsp`, `error404.jsp`, `error500.jsp`의 JSP 표준 헤더 미반영

즉, 수동 수정으로는 해결할 수 있지만 다음 생성 시 다시 발생한다. 따라서 생성기 수정이 필요하다.

## 2. 범위 구분

### 2.1 `buildBoardFeature` 범위

`buildBoardFeature`가 생성하는 BBS 전용 산출물에 대해 다음을 보장한다.

- `BbsVO`, `BbsSearchVO`, `EgovBbsServiceImpl`, 게시판 화면 템플릿이 생성 시점부터 표준을 만족
- Thymeleaf/JSP 분기별로 생성 결과가 바로 빌드 가능한 상태
- 후처리 수동 보정 없이 재생성해도 동일한 품질 유지

### 2.2 `initializeProject` 범위

초기 프로젝트 골격 생성 시 다음을 보장한다.

- `index.jsp`
- `error404.jsp`
- `error500.jsp`

이 세 파일의 JSP 표준 헤더와 경로 처리 규칙을 생성기 자체에서 반영한다.

## 3. 현재 확인된 미준수 항목

### 3.1 `buildBoardFeature` 관련

#### A. `BbsVO` 검색/페이징 계약 미정합

현재 검증기 기준으로 다음 항목이 부족하다.

- `searchKeyword`
- `pageIndex`
- `searchCondition`

보드 템플릿은 `BbsSearchVO`를 별도로 생성하지만, 실제 생성 결과에서 검색/페이징 사용이 끊기지 않도록 계약이 명확해야 한다.

#### B. `EgovBbsServiceImpl` 생성 표준 미정합

현재 생성 결과는 다음을 만족하지 못한다.

- `@RequiredArgsConstructor`
- `@Transactional`

서비스 구현체는 조회와 CUD를 모두 포함하므로 트랜잭션 경계가 생성 단계에서 반영되어야 한다.

#### C. Thymeleaf 보드 화면 템플릿의 표준화 여지

`layout/default.html`은 존재하지만, 보드 생성 결과가 후속 수정 없이도 동일한 레이아웃 정책을 일관되게 유지해야 한다.

### 3.2 `initializeProject` 관련

#### D. JSP 진입점과 오류 페이지 표준 미정합

다음 파일에 대해 UTF-8, JSTL, `<c:url>` 기반 경로 처리 규칙이 필요하다.

- `index.jsp`
- `error404.jsp`
- `error500.jsp`

이 항목은 `buildBoardFeature`가 아니라 `initializeProject`에서 수정해야 한다.

## 4. 수정 요구사항

### 4.1 `buildBoardFeature` 수정 요구사항

#### 4.1.1 `BbsVO` 생성 규칙 보강

생성 결과의 `BbsVO`에는 다음이 포함되어야 한다.

- 검색 조건 필드
- 검색 키워드 필드
- 페이지 번호 필드
- 페이지네이션에 필요한 기본 필드와의 연결

권장 구조는 다음과 같다.

- `BbsSearchVO`가 검색/페이징 기본값을 보유
- `BbsVO`가 이를 상속하거나 동일 계약을 유지
- 화면 템플릿과 컨트롤러가 `searchVO` / `resultList` / `paginationInfo`를 일관되게 사용

#### 4.1.2 `EgovBbsServiceImpl` 생성 규칙 보강

생성 결과의 서비스 구현체는 다음을 만족해야 한다.

- 생성자 주입 또는 동등한 주입 방식 명시
- CUD 메서드에 트랜잭션 경계 존재
- 조회 전용 메서드와 쓰기 메서드의 성격이 구분됨

#### 4.1.3 `buildBoardFeature` 설명 문구 갱신

Tool 설명에 다음 내용을 반영해야 한다.

- `thymeleaf` 생성 시 layout 포함 여부
- 생성 파일 수
- 생성 후 수동 보정이 필요 없다는 점

#### 4.1.4 검증 기준 갱신

현재 검증기는 “생성 후 보정 필요”를 알려주고 있다. 이후에는 다음 기준으로 바꾼다.

- 생성 결과가 즉시 통과
- 수동 수정이 필요한 항목이 0개
- Thymeleaf/JSP 모두 파일 수와 경로가 일치

### 4.2 `initializeProject` 수정 요구사항

#### 4.2.1 `index.jsp` 표준화

다음 항목을 반영한다.

- UTF-8 page directive
- JSTL core taglib 선언
- `<c:url>` 기반 forward 또는 링크 처리

#### 4.2.2 `error404.jsp`, `error500.jsp` 표준화

다음 항목을 반영한다.

- UTF-8 page directive
- JSTL core taglib 선언
- 정적 문자열 직접 연결이 아닌 표준 JSP 구조

#### 4.2.3 프로젝트 초기화 설명 문구 갱신

`ProjectInitializrTool` 설명에 다음 내용을 명시한다.

- WAR 프로젝트의 기본 JSP 표준
- 생성 직후 빌드 가능 상태
- 후속 CRUD 생성과의 연결 규칙

## 5. 구현 우선순위

1. `buildBoardFeature`의 `BbsVO`와 `EgovBbsServiceImpl` 생성 규칙 수정
2. `buildBoardFeature` 설명과 검증 메시지 정리
3. `initializeProject`의 JSP 3개 표준화
4. 관련 테스트 갱신
5. 재생성 후 `mvn clean package` 검증

## 6. 수용 기준

다음 조건을 모두 만족하면 완료로 본다.

- `buildBoardFeature(viewType="thymeleaf")` 결과에 수동 보정이 필요하지 않다.
- `BbsVO.java` 검증에서 검색/페이징 누락 항목이 없다.
- `EgovBbsServiceImpl.java` 검증에서 트랜잭션/주입 누락 항목이 없다.
- `initializeProject()` 결과의 `index.jsp`, `error404.jsp`, `error500.jsp`가 JSP 표준을 충족한다.
- 신규 생성 프로젝트에서 `mvn clean package`가 성공한다.

## 7. 관련 파일

- [CrudPromptBuilderTool.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/tools/CrudPromptBuilderTool.java)
- [BoardOrchestrationService.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/service/BoardOrchestrationService.java)
- [BoardLayerDefinition.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/model/board/BoardLayerDefinition.java)
- [BoardTemplateRenderer.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/service/BoardTemplateRenderer.java)
- [ProjectInitializrTool.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/tools/ProjectInitializrTool.java)
- [FilePlanFactory.java](/Users/jeongdaeseob/workspace-spring-ai/springai/src/main/java/com/krdevops/springai/service/initializr/FilePlanFactory.java)

## 8. 구현 반영 상태

### 완료

- `board/service-impl.java.ftl`
  - `@Transactional` 추가
  - 조회 메서드 readOnly 적용
  - CUD 메서드 트랜잭션 적용
- `FilePlanFactory.java`
  - `error404.jsp`, `error500.jsp`를 JSTL 기반 표준 JSP로 정리
  - `javascript:history.back()` 제거
  - 메인 이동 링크를 `<c:url>` 기반으로 변경
- `egov/index.jsp.tpl`
  - JSTL core taglib 추가

### 확인 필요

- `BbsVO` 검색/페이징 계약은 템플릿 구조상 충족되지만, `CodeValidatorService`가 상속 필드를 어떻게 판정하는지에 따라 오탐 가능성이 있다.
- 현재 코드 기준으로는 `buildBoardFeature`의 수동 보정 필요 항목은 해소되었다.
