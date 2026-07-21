# buildBoardFeature 미구현 목록 및 영향평가

> 작성일: 2026-07-15  
> 대상: `CrudPromptBuilderTool.buildBoardFeature` 게시판 생성기  
> 기준 문서: `buildBoardFeature_URL_PK_표시정보_CSS_구현목록.md`  
> 판정 기준: 현재 소스, 실제 DB 기반 smoke 생성 결과, `egov-thymeleaf-ui` 스킬의 보안·KRDS 검증 규칙

## 1. 결론

URL alias, `bbsId + nttId` 복합 PK 방어, `LETTNPROGRMLIST`/`LETTNBBS` 기반 표시정보, marker 기반 CSS, KRDS 크기 계약, 조건부 CSRF와 생성 결과 자동 감사까지 생성기 내부에 구현했다. 실제 DB 기반 12개 파일 재생성, 전체 테스트, `bootJar`, 생성 프로젝트 WAR 빌드와 HTTP CRUD 통합 검증도 통과했다.

구현 과정의 실제 배포 검증에서 component-scan 누락과 숫자형 `nttId` 채번 오류를 추가 발견했고, 두 문제도 생성기와 감사기에 반영했다. 따라서 **핵심 생성기 미구현 항목 GAP-001~GAP-006은 완료**다.

다만 전체 목록을 “모두 완료”로 판정하지는 않는다. 인앱 브라우저 세션을 사용할 수 없어 계산된 CSS 높이·정렬과 스크린샷 기반 시각 검증은 아직 남아 있다. 첨부 스트리밍은 의도적 제외이며, 권한과 명명·DI는 별도 정책 항목이다.

## 2. 미구현·미검증 요약

| ID | 구분 | 항목 | 현재 위험도 | 우선순위 | 판정 |
|---|---|---|---|---|---|
| GAP-001 | 구현 완료 | KRDS textarea 높이 토큰 override | 낮음 | P0 | 감사 `OVERRIDDEN`, 종료 코드 0 |
| GAP-002 | 구현 완료 | `.egov-*` 텍스트·링크 font-size 계약 | 낮음 | P1 | CSS 토큰·auditor 반영 |
| GAP-003 | 구현 완료 | 생성 결과 자동 감사 범위 확대 | 낮음 | P0 | CSS·CSRF·PK·scan·채번 감사 통과 |
| GAP-004 | 구현 완료 | 등록·수정·삭제 폼 CSRF hidden input | 낮음 | P1 | Thymeleaf/JSP 조건부 생성 |
| GAP-005 | 구현 완료 | 생성 Controller component-scan 등록 | 낮음 | P0 | 실제 DB URL 200 확인 |
| GAP-006 | 구현 완료 | 생성형 `nttId` 검증 제외 및 숫자형 안전 채번 | 낮음 | P0 | 등록·수정·논리삭제 통과 |
| VAL-001 | 부분 완료 | 실제 브라우저 통합·시각 시나리오 | 중간 | P0 | HTTP 기능 통과, 계산 스타일·스크린샷 대기 |
| EXC-001 | 의도적 제외 | 첨부파일 실제 스트리밍 | 중간 | P2 | Controller에 TODO 유지 |
| DEC-001 | 정책 결정 | 작성자·관리자 수정/삭제 권한 | 운영 시 높음 | P1 | 현재 생성 범위 밖 |
| DEC-002 | 규칙 결정 | 클래스명·DI 컨벤션 통일 | 낮음 | P3 | 기능 오류는 아님 |

## 3. 최초 미구현 항목과 구현 결과

### GAP-001. KRDS textarea 높이 토큰 override

실제 smoke 프로젝트에 스킬 감사 스크립트를 실행하면 다음 항목이 남는다.

```text
NOT OVERRIDDEN  krds-input  krds-input--textarea-size-height  14.4rem  230.4px
1건이 임계값을 넘는데 아직 override 되어 있지 않습니다.
```

현재 [styles.css.tpl](../../src/main/resources/templates/egov/styles.css.tpl)은 `.egov-textarea`에 `min-height`만 지정하고, 번들이 `.krds-input` 자신에게 선언한 `--krds-input--textarea-size-height`는 재정의하지 않는다. `min-height: 220px`은 번들의 실제 `height: 230.4px`을 줄이지 못하므로 토큰 감사도 실패하고 화면 높이 계약도 보장하지 못한다.

영향:

- 프로젝트를 새로 생성할 때마다 동일한 감사 실패가 반복된다.
- textarea가 input/select/button과 별개의 과대 높이 규칙을 유지할 수 있다.
- 구현목록의 “textarea 토큰 확인”, “감사 종료 코드 0” 완료 표시는 사실과 다르다.

권장 구현:

1. `.krds-input` 선택자에 `--krds-input--textarea-size-height`를 명시한다.
2. [KrdsStylesConfigurer.java](../../src/main/java/com/krdevops/springai/service/KrdsStylesConfigurer.java)의 marker CSS와 `styles.css.tpl` 양쪽에 같은 계약을 반영한다.
3. 두 CSS 원본의 중복으로 다시 drift가 생기지 않도록 공통 상수 또는 공통 resource fragment로 단일화하는 방안을 검토한다.
4. 생성 후 `krds_token_audit.py` 종료 코드 0을 테스트로 고정한다.

### GAP-002. `.egov-*` 텍스트·링크 font-size 계약

스킬은 커스텀 `.egov-*` 텍스트·링크가 브라우저 기본 16px로 떨어지지 않도록 `font-size` 명시를 요구한다. 현재 `styles.css.tpl`에서 다음 텍스트 성격 클래스는 색상·굵기만 있고 글자 크기가 없다.

- `.egov-primary-text`
- `.egov-detail-link`
- `.egov-file-detail-link`
- `.egov-file-empty`
- `.egov-post-nav-link`

영향:

- 목록/상세의 13~15px 텍스트 사이에서 일부 링크만 16px로 커 보일 수 있다.
- 폰트 크기 상속은 DOM 위치에 따라 달라 실제 브라우저에서만 드러나는 회귀가 생긴다.
- 기존 구현목록의 “`.egov-*` font-size 확인” 완료 표시는 범위를 축소해 다시 검증해야 한다.

권장 구현:

- 구조용 `.egov-*` 전체에 무조건 font-size를 넣지 말고, 실제 텍스트를 렌더링하는 링크·상태·보조문구 클래스만 감사한다.
- 주변 라벨 또는 테이블 본문과 동일한 `--egov-screen-*` 토큰을 사용한다.
- auditor 테스트에 “색상이나 font-weight가 있는 텍스트 클래스인데 font-size 계약이 없는 fixture”를 추가한다.

### GAP-003. 생성 결과 자동 감사 범위 확대

현재 [BoardGeneratedCodeAuditor.java](../../src/main/java/com/krdevops/springai/service/BoardGeneratedCodeAuditor.java)는 다음만 검사한다.

- Controller 복합 PK helper와 `bbsId` 기본값 처리
- DB URL alias
- Mapper XML의 `${}`
- View의 `bbsId`, `egov-crud-page`, FreeMarker 잔존
- 공통 layout 파일 존재

다음 필수 계약은 검사하지 않는다.

- `krds-input`/`krds-form-select`/`krds-btn` 크기 modifier 누락
- textarea를 포함한 KRDS sizing token override
- 레거시 eGovFrame 페이지네이션 CSS와 수직 정렬 계약
- 텍스트 성격 `.egov-*` 클래스의 font-size
- 등록·수정·삭제 폼의 CSRF
- `krds_token_audit.py`와 동등한 생성 후 판정

영향:

- 생성기는 “감사 통과”를 반환하지만 실제 스킬 감사에서는 실패하는 거짓 양성이 발생한다.
- 사용자가 매번 생성 후 수동으로 CSS·보안 항목을 찾아야 하므로 생성기 내부 완료 목표와 충돌한다.
- 향후 다른 KRDS 컴포넌트를 추가해도 과대 sizing token을 자동 탐지하지 못한다.

권장 구현:

1. 운영 MCP가 외부 Python 설치에 의존하지 않도록 핵심 계약은 Java auditor로 구현한다.
2. 스킬의 Python 감사 스크립트는 통합 테스트/CI 교차검증에 사용한다.
3. 감사 실패 시 생성 성공 메시지에 섞어 warning만 반환하지 말고, 실패 항목과 대상 파일을 구조적으로 반환한다.
4. 정상 fixture뿐 아니라 modifier/토큰/CSRF 각각을 제거한 실패 fixture 테스트를 둔다.

### GAP-004. 등록·수정·삭제 폼 CSRF

현재 다음 템플릿의 POST form에는 `_csrf` hidden input이 없다.

- [thymeleaf-regist-body.html.ftl](../../src/main/resources/templates/board/thymeleaf-regist-body.html.ftl)
- [thymeleaf-updt-body.html.ftl](../../src/main/resources/templates/board/thymeleaf-updt-body.html.ftl)
- [thymeleaf-detail-body.html.ftl](../../src/main/resources/templates/board/thymeleaf-detail-body.html.ftl)의 삭제 form

현재 smoke 프로젝트에는 Spring Security 구성이 없어 즉시 403이 발생하지는 않는다. 하지만 Security를 적용한 프로젝트에서는 등록·수정·삭제가 403으로 실패하거나, CSRF를 비활성화해 보안 수준을 낮추게 될 가능성이 있다.

영향:

- 현재 위험도는 중간이지만 Spring Security 사용 시 기능·보안 영향이 모두 높다.
- hidden input을 무조건 출력하면 `_csrf` 모델이 없는 비보안 프로젝트에서 표현식 문제가 생길 수 있다.

권장 구현:

```html
<input th:if="${_csrf != null}"
       type="hidden"
       th:name="${_csrf.parameterName}"
       th:value="${_csrf.token}">
```

- 보안 적용 여부와 무관하게 동작하도록 조건부로 생성한다.
- body/standalone 조합에서 중복 또는 누락되지 않는지 확인한다.
- JSP 생성도 지원 범위이므로 JSP POST form의 동일 계약을 함께 결정한다.
- `_csrf` 존재/부재 두 렌더링 테스트와 auditor 실패 테스트를 추가한다.

### GAP-005. 생성 Controller component-scan — 추가 발견 및 완료

초기 생성 프로젝트는 `egovframework.let.com.cmm.service`만 component-scan하고, 게시판 Controller는 `egovframework.let.cop.bbs.web`에 생성됐다. 이 때문에 컴파일과 WAR 빌드는 성공해도 실제 `.do` URL이 매핑되지 않았다.

`ThymeleafRuntimeConfigurer.ensureControllerComponentScan()`이 생성 Controller 패키지를 기존 scan 목록에 멱등 추가하도록 수정했다. 생성 감사도 해당 패키지가 `servlet-context.xml`에 있는지 검사한다. 실제 DB URL과 canonical URL의 HTTP 200으로 완료를 확인했다.

### GAP-006. 생성형 `nttId` 입력 검증·채번 — 추가 발견 및 완료

등록 화면에 없는 `nttId`에 `@NotNull`을 생성하면 Service 채번 전에 Bean Validation이 등록을 차단한다. 이를 제외한 뒤에는 `DECIMAL(20)` PK에 UUID 문자열을 `BigDecimal`로 변환해 503이 발생하는 두 번째 문제가 드러났다.

최종 구현은 다음과 같다.

- 생성형 `nttId`에는 `@NotNull`/`@NotBlank`를 생성하지 않는다.
- 문자열형 PK는 기존 `EgovIdGnrService.getNextStringId()`를 사용한다.
- 숫자형 PK는 insert와 같은 트랜잭션에서 `SELECT COALESCE(MAX(NTT_ID), 0) + 1 ... FOR UPDATE`로 채번한다.
- auditor가 검증 annotation, Service 채번 호출, Mapper 채번 쿼리와 잠금을 검사한다.
- 실제 테스트 공지를 `NTT_ID=11`로 등록하고 수정한 뒤 `USE_AT=N`으로 논리삭제해 완료를 확인했다.

## 4. 검증 대기 항목

### VAL-001. 실제 브라우저 통합·시각 검증

다음 서버 기능 시나리오는 2026-07-15 실제 WAR를 임시 Tomcat 8082에 배포해 확인했다.

- DB 등록 URL과 canonical URL: 모두 HTTP 200
- `bbsId` 없는 목록: 기본 게시판으로 HTTP 200
- `nttId` 없는 상세와 존재하지 않는 상세: 목록으로 HTTP 302
- 정상 상세: HTTP 200, 조회수 `34 → 35`
- LNB 제목 `공지사항`, 브레드크럼 `홈 > 알림정보 > 공지사항 목록`
- 등록: 숫자형 `NTT_ID=11` 자동 채번 후 목록으로 HTTP 302
- 수정: 동일 `bbsId + nttId` 유지, 제목과 수정자 갱신 후 HTTP 302
- 삭제: 물리삭제가 아닌 `USE_AT=Y → N`, 목록으로 HTTP 302
- Mapper `${}` 문자열 바인딩 0건
- KRDS 토큰 감사: textarea 토큰 `OVERRIDDEN`, 종료 코드 0

아직 남은 것은 인앱 브라우저가 필요한 다음 시각 검증이다.

- input/select/button/textarea 높이
- 페이지 번호와 처음/이전/다음/끝 링크의 수직 정렬
- 주요 화면의 스크린샷 증적

영향:

- 컴파일 성공만으로 Controller mapping 충돌, Interceptor 모델 덮어쓰기, Thymeleaf 표현식 오류를 배제할 수 없다.
- CSS custom property의 상속·특이도 문제는 정적 검사만으로 실제 적용을 확정할 수 없다.
- 따라서 이 항목은 단순 선택 QA가 아니라 릴리스 완료 게이트다.

권장 실행:

1. 사용 가능한 인앱 브라우저 세션을 연다.
2. 실제 `LETTNPROGRMLIST.URL`로 진입한다.
3. 계산된 높이와 pagination 정렬을 측정하고 스크린샷 증적을 남긴다.

## 5. 의도적으로 제외된 항목

### EXC-001. 첨부파일 실제 스트리밍

[controller.java.ftl](../../src/main/resources/templates/board/controller.java.ftl)에는 `파일 스트림 처리는 환경별로 구현` TODO가 남아 있다. 이는 기존 구현목록에서 범위 밖으로 둔 항목이므로 현재 작업의 결함으로 집계하지는 않는다.

다만 파일 링크를 노출하는 생성 옵션에서는 사용자가 다운로드를 기대하므로 다음 중 하나를 명확히 해야 한다.

- 프로젝트의 기존 `/cmm/fms/FileDown.do`를 재사용한다.
- 파일 서비스 계약을 파라미터로 받아 Controller를 생성한다.
- 구현 경로가 없으면 링크를 생성하지 않고 “첨부 다운로드 미연동”을 결과에 표시한다.

현재 상태로는 다운로드 요청이 파일 stream을 반환하지 않으므로 실제 첨부 기능을 범위에 넣는 순간 위험도는 높아진다.

## 6. 별도 정책 결정 항목

### DEC-001. 작성자·관리자 수정/삭제 권한

생성 Controller는 복합 PK와 존재 여부는 방어하지만 작성자 또는 관리자 권한을 확인하는 공통 계약은 없다. 공지사항이 운영자 전용이라면 인증·인가 계층에서 보호할 수 있으나, 범용 게시판 생성기로 사용할 경우 무권한 수정·삭제 위험이 있다.

이는 URL·PK·표시정보·CSS 구현의 누락이라기보다 보안 기능 범위 결정이다. 운영 배포 전에 다음 정책을 파라미터 또는 프로젝트 탐지 규칙으로 확정해야 한다.

- 관리자 전용
- 작성자 또는 관리자
- 외부 인가 계층에 위임

### DEC-002. 클래스명·DI 컨벤션 통일

일부 생성 클래스가 `InfoNotice*`, 일부가 `EgovInfoNotice*`인 점과 Controller가 Lombok 생성자 주입을 사용하는 점은 현재 컴파일 오류가 아니다. 기존 문서에서 명칭 전면 개명은 범위 밖으로 정했으며, Service 구현은 `@Resource` 관례를 따른다.

따라서 즉시 수정 대상이 아니라 다음 버전의 일관성 정책으로 분리한다. 기존 프로젝트 재사용 시에는 기존 이름이 우선이며, 신규 레이어만 `LETTNPROGRMLIST.PROGRM_FILE_NM` 기준을 적용하는 것이 안전하다.

## 7. 기존 구현목록에서 다시 열어야 할 항목

[buildBoardFeature_URL_PK_표시정보_CSS_구현목록.md](buildBoardFeature_URL_PK_표시정보_CSS_구현목록.md)의 다음 완료 표시는 재판정이 필요하다.

- BBI-025 textarea 높이 토큰 확인: 완료
- BBI-025 `.egov-*` font-size 확인: 정적 계약 완료
- BBI-025 감사 스크립트 종료 코드 0: 완료
- BBI-028 생성 결과 audit: CSS·CSRF·component-scan·채번까지 완료
- BBI-033 서버 기능 검증: 완료
- BBI-033 계산 스타일·스크린샷 검증: 미완료
- 최종 완료조건의 브라우저 크기·페이지네이션 시각 확정: 미완료

반면 “LETTN 계열만 지원”, “CSS가 없으면 실패”, “alias 충돌 시 warning 후 생략”, “모호한 첫 결과 사용”에 체크하지 않은 항목은 **미구현 목록이 아니라 채택하지 않은 대안**이다. 이를 결함 수에 포함하지 않는다.

## 8. 권장 구현 순서와 영향 범위

### 1단계 — CSS 계약 복구 (P0)

대상:

- `styles.css.tpl`
- `KrdsStylesConfigurer`
- 관련 단위 테스트

회귀 위험은 낮다. URL·PK·DB 조회와 독립적이다. 다만 textarea의 의도된 실제 높이를 제품 기준으로 확정해야 한다.

### 2단계 — Auditor 강화 (P0)

대상:

- `BoardGeneratedCodeAuditor`
- `BoardGeneratedCodeAuditorTest`
- 생성 orchestration의 실패 전달 방식

회귀 위험은 중간이다. 기존에 성공하던 생성이 새 계약 위반으로 실패할 수 있으므로, 먼저 생성 템플릿을 고친 뒤 auditor를 엄격하게 해야 한다.

### 3단계 — 조건부 CSRF 생성 (P1)

대상:

- Thymeleaf 등록·수정·상세 삭제 form
- 필요 시 JSP POST form
- 렌더링·auditor 테스트

회귀 위험은 중간이다. `_csrf`가 없는 프로젝트와 있는 프로젝트를 모두 테스트해야 한다. URL·PK 모델에는 영향이 없다.

### 4단계 — 전체 재생성 및 정적 게이트 (P0)

검증:

- 전체 Gradle 테스트
- `bootJar`
- 동일 입력으로 두 번 생성해 CSS `PATCHED` 후 `PRESERVED`
- KRDS 감사 0건
- 생성 프로젝트 WAR 빌드
- Mapper `${}` 0건

### 5단계 — 실제 브라우저 게이트 (P0)

VAL-001의 모든 시나리오를 통과해야 최종 완료로 판정한다.

### 6단계 — 선택 범위 확정 (P1~P3)

첨부 stream, 권한, 명명·DI 통일은 핵심 구현과 분리해 별도 작업으로 결정한다.

## 9. 최종 수용 기준

- `krds_token_audit.py`가 실제 생성 프로젝트에서 종료 코드 0을 반환한다.
- 생성 HTML의 모든 KRDS input/select/button에 크기 modifier가 있다.
- 텍스트 성격 `.egov-*` 클래스의 font-size가 명시적 토큰으로 결정된다.
- 등록·수정·삭제 form이 `_csrf` 존재/부재 환경에서 모두 정상 렌더링된다.
- Auditor가 URL·복합 PK·표시정보뿐 아니라 CSS·CSRF 위반 fixture도 실패시킨다.
- 전체 테스트, `bootJar`, smoke WAR 빌드가 통과한다.
- 동일 프로젝트에 두 번 생성해 CSS가 중복되지 않는다.
- 브라우저에서 LNB/브레드크럼/CRUD 이동/PK 방어/컨트롤 크기/페이지네이션을 확인한다.
- 위 검증 후 생성 산출물에 URL·PK·표시정보·CSS 수동 후처리가 필요하지 않다.

## 10. 종합 평가

현재 구현은 URL·PK·표시정보·CSS·CSRF와 실제 등록 채번 오류까지 생성기 내부로 흡수했다. 정적 감사와 서버 기능 기준으로는 매번 생성 후 수동 후처리하던 핵심 문제를 닫았다.

최종 판정은 **핵심 구현 완료, 전체 검증 부분 완료**다. 인앱 브라우저가 제공되면 계산 스타일과 pagination 정렬 및 스크린샷만 추가 확인하면 VAL-001을 닫을 수 있다. 첨부 스트리밍·권한·명명 규칙은 본 핵심 범위와 분리된 제외/정책 항목으로 유지한다.
