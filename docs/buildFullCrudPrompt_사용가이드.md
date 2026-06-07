    # buildFullCrudPrompt() 사용 가이드

eGovFrame 5.x CRUD 소스 자동 생성 MCP 툴 사용법

---

## 1. 개요

`buildFullCrudPrompt()`는 DB 테이블 하나로 eGovFrame 5.x 표준 CRUD 소스 11개 파일을 자동 생성하는 MCP 툴입니다.

### 생성 파일 목록

| 파일 | 레이어 |
|------|--------|
| `EmployeeVO.java` | VO |
| `EmployeeMapper.java` | MyBatis Mapper 인터페이스 |
| `EmployeeMapper.xml` | MyBatis Mapper XML |
| `EmployeeService.java` | Service 인터페이스 |
| `EgovEmployeeServiceImpl.java` | ServiceImpl 구현체 |
| `EgovEmployeeController.java` | Spring MVC Controller |
| `EgovEmployeeValidationHandler.java` | Validation 전역 예외 핸들러 |
| `EgovEmployeeList.jsp` | 목록 JSP |
| `EgovEmployeeDetail.jsp` | 상세 JSP |
| `EgovEmployeeRegist.jsp` | 등록 JSP |
| `EgovEmployeeUpdt.jsp` | 수정 JSP |

---

## 2. 파라미터

| 파라미터 | 필수 | 설명 | 예시 |
|----------|------|------|------|
| `database` | ✅ | DB명 | `com` |
| `tableName` | ✅ | 테이블명 | `COMTNEMPLYRINFO` |
| `domain` | ✅ | 도메인명 (대문자 시작) | `Employee` |
| `packageName` | ✅ | 패키지 경로 | `egovframework.let.emp` |
| `outputPath` | ✅ | 저장 절대경로 | `/Users/.../employee` |
| `llmProvider` | ✅ | 생성 방식 | `auto` or `claude` |

### outputPath 자동 결정 규칙

```
1. 사용자가 경로 명시        → 그대로 사용
2. 기존 프로젝트 경로 언급   → resolveProjectOutputPath() 먼저 호출
3. 경로 미언급              → getDefaultOutputPath(domain) 호출
                              기본값: ~/Desktop/egov-generated/{domain}
```

---

## 3. 호출 방법

### 방법 1 — 자연어 요청 (가장 간단)

```
COMTNEMPLYRINFO 테이블로 eGovFrame CRUD 소스 생성해줘
```

Claude가 자동으로 `buildFullCrudPrompt()`를 선택해서 호출합니다.

### 방법 2 — 툴 명시 요청

```
buildFullCrudPrompt() 써서 COMTNEMPLYRINFO 테이블 소스 생성해줘
패키지는 egovframework.let.emp, auto 모드로
```

### 방법 3 — 파라미터 직접 명시

```
buildFullCrudPrompt() 호출해줘
- database: com
- tableName: COMTNEMPLYRINFO
- domain: Employee
- packageName: egovframework.let.emp
- outputPath: /Users/jeongdaeseob/Desktop/egov-generated/employee
- llmProvider: auto
```

---

## 4. llmProvider 방식 비교

### auto 방식

```
buildFullCrudPrompt(llmProvider: "auto") 호출
  → Tool 내부에서 11개 파일 직접 생성·저장
  → 결과 요약만 Claude에게 반환
```

### claude 방식

```
1. buildFullCrudPrompt() 호출 → 통합 프롬프트 반환
2. Claude가 getCodeTemplate() × 11회 호출
3. 플레이스홀더 채워서 소스 생성
4. saveGeneratedCode() × 11회 저장
```

### 비교표

| 항목 | `auto` | `claude` |
|------|--------|----------|
| Claude 토큰 소비 | ~1,500 토큰 | ~40,000 토큰 |
| 절감률 | **97% 절감** | 기준 |
| 소스 품질 제어 | Tool 내부 템플릿 고정 | Claude가 직접 판단 |
| 커스터마이징 | 템플릿 수정 필요 | 즉시 반영 가능 |
| 미준수 항목 수정 | 재생성 필요 | 즉시 수정 가능 |
| 특정 레이어 재생성 | 불가 | 가능 |

### 선택 기준

```
처음 생성 (빠르게)  → auto
검증 후 수정 필요   → claude (특정 레이어만 재생성)
커스터마이징 필요   → claude
```

---

## 5. claude 방식 상세 동작

### Claude 내부 처리 순서

```
① buildFullCrudPrompt() 호출
   → 테이블 스키마 + 플레이스홀더 매핑 프롬프트 반환

② getCodeTemplate("vo") 호출
   → 템플릿 받아서 플레이스홀더 채워 EmployeeVO.java 생성
   → saveGeneratedCode() 저장

③ getCodeTemplate("mapper") 호출
   → EmployeeMapper.java 생성·저장

④ getCodeTemplate("mapperXml") 호출
   → EmployeeMapper.xml 생성·저장

⑤ ~ ⑪ 나머지 8개 레이어 동일하게 반복
```

### claude 방식 장점

| 상황 | 이유 |
|------|------|
| 미준수 항목 즉시 수정 | Claude가 검증 결과 보고 바로 재생성 가능 |
| 커스터마이징 필요 | "목록에 검색 필드 추가해줘" 등 즉시 반영 |
| 특정 레이어만 재생성 | `getCodeTemplate("mapperXml")` 단독 호출 가능 |
| 코드 리뷰 동시 진행 | 생성하면서 Claude가 내용 검토 |

### 특정 레이어만 재생성 예시

```
getCodeTemplate("mapperXml") 써서
COMTNEMPLYRINFO의 EmployeeMapper.xml 다시 생성해줘
쿼리 ID selectList, selectTotCnt, insert, update, delete 포함해서
```

---

## 6. getCodeTemplate() layer 목록

| layer 값 | 생성 파일 |
|----------|----------|
| `vo` | VO (Value Object) |
| `controller` | Spring MVC Controller |
| `service` | Service 인터페이스 |
| `serviceImpl` | ServiceImpl 구현체 |
| `mapper` | MyBatis Mapper 인터페이스 |
| `mapperXml` | MyBatis Mapper XML |
| `jspList` | 목록 JSP |
| `jspDetail` | 상세 JSP |
| `jspRegist` | 등록 JSP |
| `jspUpdt` | 수정 JSP |
| `controlleradvice` | Validation 전역 예외 핸들러 |

---

## 7. 소스 생성 규칙 (반드시 준수)

1. `buildFullCrudPrompt()`가 제공한 플레이스홀더 값만 대입
2. 템플릿에 없는 메서드, 주석, import 추가 금지
3. 플레이스홀더가 없는 줄은 한 글자도 변경 금지
4. 클래스 선언·어노테이션·상속·패키지 구조 그대로 유지
5. import는 템플릿에 명시된 항목만 사용
6. `{{DOMAIN_KR}}` 등 한국어 플레이스홀더는 임의 추론 금지