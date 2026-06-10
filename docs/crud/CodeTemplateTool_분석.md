# CodeTemplateTool 분석

작성일: 2026-05-21

---

## 한 줄 요약

eGovFrame 5.x 표준 CRUD 소스의 **레이어별 템플릿 문자열을 반환**하는 MCP Tool.
직접 파일을 생성하지 않고, Claude가 플레이스홀더를 치환한 뒤 `saveGeneratedCode()` 등으로 저장한다.

---

## 역할 구분

```
ProjectInitializrService       CodeTemplateTool              Claude (AI)
─────────────────────          ─────────────────────         ─────────────────
프로젝트 골격 생성             레이어별 템플릿 반환           플레이스홀더 치환
(디렉터리 + 설정 파일)         (소스 코드 뼈대)              (실제 소스 완성)
```

`ProjectInitializrService`는 한 번만 호출되어 프레임 전체를 만든다.
`CodeTemplateTool`은 CRUD 도메인마다 레이어별로 반복 호출된다.

---

## 진입점

```java
@Tool(description = "...")
public String getCodeTemplate(String layer)
```

`layer` 문자열을 받아 switch로 해당 private 메서드를 호출한 뒤 템플릿 문자열을 반환한다.

---

## 지원 레이어 10종

| layer 파라미터 | 메서드 | 생성 파일명 규칙 |
|---|---|---|
| `vo` | `voTemplate()` | `{DOMAIN}VO.java` |
| `controller` | `controllerTemplate()` | `Egov{DOMAIN}Controller.java` |
| `service` | `serviceTemplate()` | `{DOMAIN}Service.java` |
| `serviceImpl` | `serviceImplTemplate()` | `Egov{DOMAIN}ServiceImpl.java` |
| `mapper` | `mapperTemplate()` | `{DOMAIN}Mapper.java` |
| `mapperXml` | `mapperXmlTemplate()` | `{DOMAIN}Mapper.xml` |
| `jspList` | `jspListTemplate()` | `Egov{DOMAIN}List.jsp` |
| `jspDetail` | `jspDetailTemplate()` | `Egov{DOMAIN}Detail.jsp` |
| `jspRegist` | `jspRegistTemplate()` | `Egov{DOMAIN}Regist.jsp` |
| `jspUpdt` | `jspUpdtTemplate()` | `Egov{DOMAIN}Updt.jsp` |

---

## 플레이스홀더 체계

Claude가 `buildFullCrudPrompt()`가 제공한 값으로 치환하는 마커들:

| 플레이스홀더 | 예시 | 사용 레이어 |
|---|---|---|
| `{{PACKAGE}}` | `egovframework.let.emp` | 모든 Java 레이어 |
| `{{DOMAIN}}` | `Employer` | 모든 레이어 |
| `{{DOMAIN_LC}}` | `employer` | Controller, ServiceImpl, JSP |
| `{{DOMAIN_KR}}` | `직원` | JSP |
| `{{TABLE_NAME}}` | `COMTNEMPLYRINFO` | VO, Mapper XML |
| `{{VO_FIELDS}}` | `private String emplyrId;` | VO |
| `{{PK_FIELD}}` | `emplyrId` | Controller, Mapper, JSP |
| `{{PK_COLUMN}}` | `EMPLYR_ID` | Mapper XML |
| `{{PK_TYPE}}` | `String` | (Mapper 시그니처용) |
| `{{MAPPER_COLUMNS}}` | `EMPLYR_ID, USER_NM, ...` | Mapper XML |
| `{{INSERT_COLUMNS}}` | `EMPLYR_ID, USER_NM` | Mapper XML |
| `{{INSERT_VALUES}}` | `#{emplyrId}, #{userNm}` | Mapper XML |
| `{{UPDATE_SET}}` | `USER_NM=#{userNm}, ...` | Mapper XML |
| `{{RESULT_MAP_FIELDS}}` | `<result .../>` 반복 | Mapper XML |
| `{{JSP_LIST_TH}}` | `<th>이름</th>` 반복 | jspList |
| `{{JSP_LIST_TD}}` | `<td>${item.userNm}</td>` 반복 | jspList |
| `{{JSP_DETAIL_ROWS}}` | `<th>이름</th><td>...</td>` 반복 | jspDetail |
| `{{JSP_FORM_INPUTS}}` | `<input name="userNm"/>` 반복 | jspRegist, jspUpdt |
| `{{URL_PREFIX}}` | `/emp/employer` | Controller, JSP |
| `{{DATE}}` | `2026-05-21` | 모든 Java 레이어 |

---

## 레이어별 핵심 내용

### VO (`voTemplate`)
- Lombok `@Getter @Setter`
- eGovFrame `PaginationInfo` 필드 포함 (페이징/검색 공통 필드 기본 탑재)
- `{{VO_FIELDS}}` 위치에 컬럼 기반 필드가 삽입됨

### Controller (`controllerTemplate`)
- `@ModelAttribute` / `ModelMap` 패턴 — `javax.servlet.*` import **없음**
- `EgovPropertyService`로 페이지 단위(`pageUnit`, `pageSize`) 설정 읽기
- `PaginationInfo` 생성 및 서비스 호출 후 `model.addAttribute`로 결과 전달
- CRUD 6개 메서드: List / Detail / RegistView / Regist / UpdtView / Updt / Delete

### Service (`serviceTemplate`)
- 순수 인터페이스. 6개 메서드 시그니처만 정의.

### ServiceImpl (`serviceImplTemplate`)
- `EgovAbstractServiceImpl` 상속 (eGovFrame 표준)
- `@Service("{{DOMAIN_LC}}Service")` — 빈 이름을 소문자 도메인명으로 고정
- insert/update/delete에 `@Transactional` 적용

### Mapper (`mapperTemplate`)
- `EgovAbstractMapper` 상속 + `@Mapper` 어노테이션
- 인터페이스만. SQL은 mapperXml이 담당.

### Mapper XML (`mapperXmlTemplate`)
- namespace: `{{PACKAGE}}.service.impl.{{DOMAIN}}Mapper`
- `resultMap` 기반 컬럼↔필드 매핑
- CRUD 5개 쿼리 + `<sql id="searchCondition">` (searchCondition/searchKeyword 동적 WHERE)
- 페이징: `LIMIT #{paginationInfo.firstRecordIndex}, #{recordCountPerPage}`

### JSP 4종
- **List**: 검색 폼 + `<c:forEach>` 목록 테이블 + `ui:pagination` + 등록 버튼
- **Detail**: 상세 테이블 + 수정/목록/삭제 버튼 (삭제는 hidden form으로 POST)
- **Regist**: 등록 폼 (`{{JSP_FORM_INPUTS}}` 삽입)
- **Updt**: 수정 폼 (PK hidden + `{{JSP_FORM_INPUTS}}` 삽입)
- JSTL taglib: `http://java.sun.com/jsp/jstl/core` (eGovFrame UI 태그: `ui`)

---

## 패키지 구조 규칙

생성되는 소스는 아래 eGovFrame 표준 패키지 구조를 따른다:

```
{PACKAGE}/
├── web/
│   └── Egov{DOMAIN}Controller.java
├── service/
│   ├── {DOMAIN}Service.java    (인터페이스)
│   └── {DOMAIN}VO.java
└── service/impl/
    ├── Egov{DOMAIN}ServiceImpl.java
    └── {DOMAIN}Mapper.java

resources/egovframework/mapper/{domain}/
└── {DOMAIN}Mapper.xml

webapp/WEB-INF/jsp/egovframework/{domain}/
├── Egov{DOMAIN}List.jsp
├── Egov{DOMAIN}Detail.jsp
├── Egov{DOMAIN}Regist.jsp
└── Egov{DOMAIN}Updt.jsp
```

---

## 호출 흐름

```
사용자: "COMTNEMPLYRINFO 테이블로 직원 CRUD 만들어줘"
    │
    ▼
buildFullCrudPrompt()          ← 테이블 스키마 조회 + 플레이스홀더 값 결정
    │  플레이스홀더 매핑 결과 반환
    ▼
getCodeTemplate("vo")          ← voTemplate() 반환
getCodeTemplate("controller")  ← controllerTemplate() 반환
getCodeTemplate("service")     ← serviceTemplate() 반환
...  (10개 레이어 순서대로)
    │  Claude가 각 템플릿에 플레이스홀더 치환
    ▼
saveGeneratedCode(filePath, code)  ← 치환 완료 소스를 파일로 저장
```

---

## 설계 제약 — Claude가 반드시 준수해야 하는 규칙

Tool description에 명시된 6개 규칙:

1. `buildFullCrudPrompt()`가 제공한 플레이스홀더 값만 대입
2. 템플릿에 없는 메서드·주석·import 추가 금지
3. 플레이스홀더 없는 줄은 한 글자도 변경 금지
4. 클래스 선언·어노테이션·상속·패키지 구조 유지
5. import는 템플릿에 명시된 항목만 사용
6. 한국어 플레이스홀더는 buildFullCrudPrompt() 값을 그대로 사용 (임의 추론 금지)

→ AI 소스 일관성 통제의 핵심 장치. `CodeTemplateTool`이 "골격 고정" 역할을 하고
  Claude는 값 치환만 수행하도록 역할을 제한한다.

---

## 현재 제약 / 알려진 이슈

| 항목 | 상태 | 비고 |
|---|---|---|
| egovVersion 미수신 | ⚠️ | 4.3/5.0 구분 없이 동일 템플릿 반환 |
| javax.servlet import | ✅ 영향 없음 | Controller가 @ModelAttribute/ModelMap 사용 |
| javax.validation import | ✅ 영향 없음 | VO에 validation annotation 없음 |
| JSTL taglib URI | ✅ 영향 없음 확인 | `http://java.sun.com/jsp/jstl/core` 고정이지만, `org.glassfish.web:jakarta.servlet.jsp.jstl:3.0.1` 구현체가 하위 호환 지원 → Tomcat 10 실배포 테스트에서 정상 작동 확인 |
| JSP 파일 — egovVersion 무관 | ⚠️ | 5.0 프로젝트에도 4.3 스타일 JSP 생성됨 (현재 런타임 영향 없음) |
