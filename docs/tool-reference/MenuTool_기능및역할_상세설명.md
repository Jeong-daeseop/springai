# MenuTool 기능 및 역할 상세 설명

## 개요

`MenuTool`은 **eGovFrame COMTNMENUINFO 메뉴 트리 조회 및 신규 메뉴 등록 SQL 자동 생성**을 담당하는 MCP Tool입니다.
메뉴 구조 파악부터 프로그램·메뉴 등록 SQL 생성까지 메뉴 관리 전 과정을 지원합니다.

---

## 구성 레이어

```
MenuTool (MCP Tool 진입점)
  └── MenuService (오케스트레이터)
        ├── MenuInputValidator   — 입력값 검증 및 URL/번호 정규화
        ├── MenuRepository       — DB 조회 (트리 탐색, 중복 검사, MAX값 계산)
        ├── MenuSqlBuilder       — SQL 생성 로직
        └── MenuResultBuilder    — 결과 포맷팅
```

---

## 기능 1: `getMenuStructure(menuNo)` — 메뉴 트리 조회

### 목적
`COMTNMENUINFO`의 메뉴 계층 구조를 트리 형태로 시각화하고, 신규 등록 시 권장 MENU_NO / MENU_ORDR을 자동 계산

### 파라미터

| 값 | 동작 |
|----|------|
| `"0"` | 전체 트리 반환 (최상위부터 재귀 탐색) |
| `"6000000"` | 해당 메뉴 + 직속 하위 목록 반환 |

### 트리 렌더링 예시
```
[6000000] 시스템관리
  ├── [6010000] 공통분류코드
  ├── [6020000] 공통상세코드
  └── [6310000] 장애처리결과관리

【권장값】
신규 MENU_NO: 6320000      ← MAX(MENU_NO) + 10000
신규 MENU_ORDR: 32         ← MAX(MENU_ORDR) + 1
```

### MENU_NO 자동 계산 규칙
- `MAX(MENU_NO) WHERE UPPER_MENU_NO = ?` + **10000**
- `MAX(MENU_ORDR) WHERE UPPER_MENU_NO = ?` + **1**

---

## 기능 2: `generateMenuInsertSql(upperMenuNo, urlPrefix, menuNm, progrmFileNm)` — 메뉴 등록 SQL 생성

### 목적
신규 메뉴 등록에 필요한 `COMTNPROGRMLIST` + `COMTNMENUINFO` INSERT SQL 2개 자동 생성

### 파라미터

| 파라미터 | 설명 | 예시 |
|----------|------|------|
| `upperMenuNo` | 상위 메뉴 번호 | `6000000` |
| `urlPrefix` | URL 경로 접두사 | `/emp/employer` |
| `menuNm` | 메뉴명 | `직원관리` |
| `progrmFileNm` | 프로그램 파일명 (PK) | `EgovEmployerList` |

### 처리 흐름 (3단계 사전 검증 포함)

```
1. MenuInputValidator
   - 숫자 검증 (upperMenuNo)
   - URL 정규화 (앞 "/" 자동 추가, 끝 "/" 제거)
   - 필수 파라미터 null/blank 체크

2. MenuRepository — 3중 중복 검증
   ① existsUpperMenu()    — 상위 메뉴 존재 여부
   ② existsProgrmFileNm() — PROGRM_FILE_NM PK 중복 여부
   ③ existsUrl()          — URL 중복 여부
   → 검증 실패 시 즉시 오류 메시지 반환 (SQL 미생성)

3. MENU_NO / MENU_ORDR 자동 계산
   - MAX(MENU_NO) + 10000
   - MAX(MENU_ORDR) + 1

4. MenuSqlBuilder — SQL 2개 생성
   ① COMTNPROGRMLIST INSERT
   ② COMTNMENUINFO INSERT

5. MenuResultBuilder — 포맷팅 후 반환
```

### 생성되는 SQL

```sql
-- ① 프로그램 등록
INSERT INTO COMTNPROGRMLIST (
  PROGRM_FILE_NM, PROGRM_STRE_PATH, PROGRM_KOREAN_NM, PROGRM_DC, URL
) VALUES (
  'EgovEmployerList',
  '/emp/employer/',
  '직원관리',
  '직원관리 프로그램',
  '/emp/employer/EgovEmployerList.do'
);

-- ② 메뉴 등록
INSERT INTO COMTNMENUINFO (
  MENU_NO, UPPER_MENU_NO, MENU_NM, PROGRM_FILE_NM, MENU_ORDR
) VALUES (
  6320000, 6000000, '직원관리', 'EgovEmployerList', 32
);
```

### URL 자동 조합 규칙
```
urlPrefix + "/" + progrmFileNm + ".do"
= /emp/employer  +  /  +  EgovEmployerList  +  .do
= /emp/employer/EgovEmployerList.do
```

---

## 중요 제약사항

| 항목 | 내용 |
|------|------|
| SQL 실행 | **직접 DB 실행 안 함** — SQL 반환만, 사용자가 검토 후 실행 |
| MENU_NO 계산 | 생성 시점 기준 — 실행 직전 중복 재확인 필요 |
| Security 반영 | SQL 실행 후 `AuthTool.generateAuthInsertSql()`로 권한 SQL 추가 필요 |
| 서버 재기동 | 메뉴 + 권한 SQL 실행 후 Spring Security 재기동 필요 |
| DB 방언 | `app.sql.dialect` 설정 기준 (`mysql_mariadb` / `oracle` / `auto`) |

---

## 전체 워크플로우 (신규 메뉴 등록 시)

```
Step 1. getMenuStructure("0") 또는 getMenuStructure("상위MENU_NO")
        → 등록 위치 파악 + 권장 MENU_NO / MENU_ORDR 확인

Step 2. AuthTool.getProgramList("키워드")
        → PROGRM_FILE_NM / URL 중복 확인

Step 3. generateMenuInsertSql(upperMenuNo, urlPrefix, menuNm, progrmFileNm)
        → COMTNPROGRMLIST + COMTNMENUINFO INSERT SQL 생성

Step 4. SQL 검토 후 DB 직접 실행

Step 5. AuthTool.generateAuthInsertSql(urlPrefix, programNm, domain)
        → Spring Security URL 권한 SQL 생성 및 실행

Step 6. 서버 재기동 또는 Security 캐시 갱신

Step 7. 메뉴 노출 및 URL 접근 권한 테스트
```

---

## AuthTool과의 관계

```
MenuTool                          AuthTool
────────────────────────────────────────────────────
COMTNPROGRMLIST INSERT  ←→  COMTNPROGRMLIST 중복 검색
COMTNMENUINFO INSERT          COMTNROLEINFO INSERT
                              COMTNAUTHORROLERELATE INSERT
```

- `MenuTool`은 **메뉴/프로그램 등록** 담당
- `AuthTool`은 **URL 접근권한 등록** 담당
- **두 Tool을 함께 사용해야** 메뉴 노출 + 접근 제어가 모두 완성됨

---

## 테스트 예시문

### getMenuStructure
```
전체 메뉴 트리 조회해줘 (menuNo=0)
```
```
시스템관리(6000000) 하위 메뉴 목록 조회해줘
```
```
사용자지원(5000000) 하위에 신규 메뉴 등록 시 권장 MENU_NO 알려줘
```

### generateMenuInsertSql
```
upperMenuNo=6000000, urlPrefix=/emp/employer, menuNm=직원관리, progrmFileNm=EgovEmployerList 로 메뉴 등록 SQL 생성해줘
```
```
시스템관리 하위에 직원관리 메뉴 추가 SQL 만들어줘
- 상위메뉴: 6000000
- URL: /emp/employer
- 메뉴명: 직원관리
- 프로그램파일명: EgovEmployerList
```

### 전체 흐름 조합
```
1. getMenuStructure("6000000") 으로 시스템관리 하위 확인
2. AuthTool.getProgramList("Employer") 로 중복 확인
3. generateMenuInsertSql("6000000", "/emp/employer", "직원관리", "EgovEmployerList") 로 SQL 생성
4. SQL 실행 후 AuthTool.generateAuthInsertSql("/emp/employer", "직원관리", "emp") 로 권한 SQL 생성
```

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `tools/MenuTool.java` | MCP Tool 진입점 (`@Tool` 어노테이션) |
| `service/MenuService.java` | 비즈니스 로직 오케스트레이터 |
| `service/menu/MenuInputValidator.java` | 입력값 검증 및 URL/번호 정규화 |
| `service/menu/MenuRepository.java` | DB 조회 (트리 탐색, 중복 검사, MAX값) |
| `service/menu/MenuSqlBuilder.java` | SQL 생성 로직 |
| `service/menu/MenuResultBuilder.java` | 결과 포맷팅 |
| `model/MenuRegistrationSpec.java` | 입력값 VO (record) |
| `model/SqlPlan.java` | SQL 결과 VO (statements, warnings, nextSteps) |
