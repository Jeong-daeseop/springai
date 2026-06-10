# AuthTool 기능 및 역할 상세 설명

## 개요

`AuthTool`은 **eGovFrame Spring Security URL 접근제어 설정을 자동화**하는 MCP Tool입니다.
신규 메뉴/도메인을 개발했을 때 DB에 필요한 보안 설정 SQL을 생성해주는 역할입니다.

---

## 구성 레이어

```
AuthTool (MCP Tool 진입점)
  └── AuthService (오케스트레이터)
        ├── AuthInputValidator   — 입력값 검증 및 URL 정규화
        ├── AuthRepository       — DB 조회 (프로그램 검색, 다음 ROLE_CODE 계산)
        ├── AuthSqlBuilder       — SQL 생성 로직
        └── AuthResultBuilder    — 결과 포맷팅
```

---

## 기능 1: `getProgramList(keyword)` — 프로그램 중복 확인

### 목적
신규 도메인 등록 전 `COMTNPROGRMLIST`에서 중복 여부 확인

### 동작
- `PROGRM_FILE_NM`, `PROGRM_KOREAN_NM`, `URL` 3개 컬럼 LIKE 검색
- keyword 미입력 시 전체 50건 반환
- MySQL/Oracle 방언 자동 분기 (`LIMIT 50` vs `FETCH FIRST 50 ROWS ONLY`)

### 출력 예시
```
PROGRM_FILE_NM                 PROGRM_KOREAN_NM               URL
----------------------------------------------------------------------------------------------------
EgovEmployerList               직원목록조회                     /emp/employer/EgovEmployerList.do
총 1건
```

> `PROGRM_FILE_NM`이 PK이므로 **INSERT 전 반드시 중복 확인 필수**

---

## 기능 2: `generateAuthInsertSql(urlPrefix, programNm, domain)` — 접근제어 SQL 생성

### 목적
신규 URL 패턴에 대한 Spring Security 롤 등록 SQL 자동 생성

### 파라미터

| 파라미터 | 설명 | 예시 |
|----------|------|------|
| `urlPrefix` | URL 경로 접두사 | `/emp/employer` |
| `programNm` | 프로그램 한국어명 | `직원관리` |
| `domain` | 도메인 식별자 | `emp` |

### 처리 흐름

```
1. AuthInputValidator — 입력 검증 및 URL 정규화
   - urlPrefix 앞 "/" 자동 추가
   - urlPrefix 끝 "/" 자동 제거

2. AuthRepository — 다음 ROLE_CODE 번호 계산
   - SELECT MAX(ROLE_CODE) FROM COMTNROLEINFO WHERE ROLE_CODE LIKE 'web-%'
   - DB 방언에 따라 MAX 추출 함수 분기

3. AuthSqlBuilder — 3개의 SQL 생성
   ① COMTNROLEINFO INSERT       — 롤 등록
   ② COMTNAUTHORROLERELATE INSERT — ROLE_ADMIN에 롤 연결
   ③ ROLE_USER 연결 SQL          — 주석 처리 (필요 시 활성화)
```

### 생성되는 SQL

```sql
-- ① 롤 등록
INSERT INTO COMTNROLEINFO (
  ROLE_CODE, ROLE_NM, ROLE_PTTRN, ROLE_DC, ROLE_TY, ROLE_SORT, CREAT_DT, MDFCN_DT
) VALUES (
  'web-000001',
  'emp 직원관리 접근권한',
  '\A/emp/employer/.*\.do.*\Z',
  'emp 직원관리 접근권한 설명',
  'url', 1, NOW(), NOW()
);

-- ② 관리자 권한 연결
INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT)
VALUES ('ROLE_ADMIN', 'web-000001', NOW());

-- ③ 일반 사용자 연결 (주석, 필요 시 활성화)
-- INSERT INTO COMTNAUTHORROLERELATE (AUTHOR_CODE, ROLE_CODE, CREAT_DT)
-- VALUES ('ROLE_USER', 'web-000001', NOW());
```

### ROLE_PTTRN 생성 규칙

Spring Security의 `FilterSecurityInterceptor`가 이 regex 패턴으로 URL 접근 여부를 판단합니다.

```
urlPrefix = /emp/employer
→ ROLE_PTTRN = \A/emp/employer/.*\.do.*\Z
```

| 구성 요소 | 의미 |
|-----------|------|
| `\A` | 문자열 시작 |
| `/emp/employer/` | URL 접두사 |
| `.*` | 임의 문자열 |
| `\.do` | `.do` 확장자 |
| `.*` | 쿼리스트링 등 |
| `\Z` | 문자열 끝 |

---

## 중요 제약사항

| 항목 | 내용 |
|------|------|
| SQL 실행 | **직접 DB 실행 안 함** — SQL 반환만, 사용자가 검토 후 실행 |
| Security 반영 | `SecurityTemplateTool`로 `securityMapper` 포함 설정 생성 필요 |
| 서버 재기동 | SQL 실행 후 Spring Security 재기동 또는 캐시 갱신 필요 |
| Race condition | ROLE_CODE 계산은 조회 시점 기준 — 동시 실행 시 중복 가능 |
| DB 방언 | `app.sql.dialect` 설정 기준 (`mysql_mariadb` / `oracle` / `auto`) |

---

## 전체 워크플로우 (신규 메뉴 등록 시)

```
Step 1. getProgramList("키워드")
        → 중복 여부 확인 (PROGRM_FILE_NM이 PK이므로 필수)

Step 2. generateAuthInsertSql(urlPrefix, programNm, domain)
        → 롤 등록 SQL 생성

Step 3. 생성된 SQL 검토 후 DB 직접 실행
        (executeQuery는 INSERT 차단 — DB 클라이언트 또는 관리 도구 사용)

Step 4. SecurityTemplateTool로 securityMapper 포함 설정 생성 확인
        (미완료 시 Security에 반영되지 않음)

Step 5. 서버 재기동 또는 Spring Security 캐시 갱신

Step 6. URL 접근 권한 테스트
        예: /emp/employer/EgovEmployerList.do 접근 확인
```

---

## 테스트 예시문

### getProgramList
```
"직원" 키워드로 프로그램 목록 검색해줘
```
```
"employer" 키워드로 프로그램 중복 확인해줘
```
```
"/emp/" URL로 등록된 프로그램 있는지 확인해줘
```

### generateAuthInsertSql
```
urlPrefix=/emp/employer, programNm=직원관리, domain=emp 로 권한 등록 SQL 생성해줘
```
```
/board/notice 게시판 공지사항 메뉴에 대한 롤 등록 SQL 생성해줘 (domain: board)
```

### 2단계 조합 흐름
```
1. "EgovEmployer" 키워드로 프로그램 목록 조회해줘 (중복 확인)
2. 중복 없으면 urlPrefix=/emp/employer, programNm=직원관리, domain=emp 로 권한 INSERT SQL 생성해줘
```

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `tools/AuthTool.java` | MCP Tool 진입점 (`@Tool` 어노테이션) |
| `service/AuthService.java` | 비즈니스 로직 오케스트레이터 |
| `service/auth/AuthInputValidator.java` | 입력값 검증 및 URL 정규화 |
| `service/auth/AuthRepository.java` | DB 조회 (프로그램 검색, ROLE_CODE 계산) |
| `service/auth/AuthSqlBuilder.java` | SQL 생성 로직 |
| `service/auth/AuthResultBuilder.java` | 결과 포맷팅 |
| `model/AuthRegistrationSpec.java` | 입력값 VO (record) |
| `model/SqlPlan.java` | SQL 결과 VO (statements, warnings, nextSteps) |
