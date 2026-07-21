# 디자인 참조 기반 CRUD 생성 잔여 외부 의존 항목 처리 가이드

> 작성일: 2026-07-17  
> 관련 문서: `local-vision-design-reference-integration-review.md`  
> 입력 작성 방법: `design-vision-external-decisions-input-guide.md`  
> 대상: Board 카테고리 공통코드, 파일 업로드, Vision provider, 브라우저 시각 회귀

## 1. 개요

현재 저장소 안에서 결정할 수 있는 Vision 분석·화면명세·CRUD 생성·정적 품질 검사 기능은 구현되어 있다.
남은 항목은 단순 코드 누락이 아니라 기관 정책, 실제 데이터, 배포망 또는 실행 대상 프로젝트가 있어야
확정할 수 있는 작업이다.

남은 항목은 다음과 같다.

| 항목 | 선행 결정 | 구현 결과 |
|---|---|---|
| Board 카테고리 공통코드 | 컬럼과 승인 `CODE_ID` | 등록·수정 select, 목록·상세 코드명 표시, 서버 검증 |
| 파일 업로드 | 저장소·보안·권한·보존 정책 | 업로드·다운로드·삭제 전체 흐름 |
| Vision provider | 망분리·외부 전송·모델 승인 | OpenAI 또는 Ollama 활성화 |
| 브라우저 시각 회귀 | 실행 URL·고정 데이터·기준 이미지 | 360/768/1280px 자동 비교 |

## 2. 권장 처리 순서

```text
1. 배포 환경 및 Vision provider 승인
        ↓
2. Board 카테고리 공통코드 그룹 확정
        ↓
3. 파일 업로드 정책 확정 및 구현
        ↓
4. 실제 대상 프로젝트 생성·실행
        ↓
5. 브라우저 시각 회귀 기준선 승인
```

공통코드 조사는 provider 승인과 병행할 수 있다. 파일 업로드와 시각 회귀는 실제 배포 환경과 대상
프로젝트가 확정된 후 처리하는 것이 안전하다.

---

## 3. Board 카테고리 공통코드 그룹

### 3.1 자동 확정하지 않는 이유

`CATEGORY`, `CTGRY_CODE`, `NOTICE_SE`와 같은 컬럼을 발견해도 어떤 공통코드 그룹과 연결해야 하는지는
물리 스키마만으로 확정할 수 없다. 잘못된 `CODE_ID`를 자동 선택하면 다른 업무의 코드가 정상 값처럼
저장될 수 있다.

따라서 생성기는 다음 정보를 승인받기 전까지 카테고리 필드를 일반 입력 또는 `UNMAPPED` 상태로 둔다.

- 대상 화면
- 대상 컬럼
- 승인 `CODE_ID`
- 필수 여부
- 기본 코드값
- 사용 중지 코드 처리 방식

### 3.2 처리 절차

1. 대상 Board 테이블의 카테고리 후보 컬럼을 확인한다.
2. `searchCommonCode()` 또는 DB 조회로 후보 코드 그룹을 찾는다.
3. 업무 담당자가 사용할 `CODE_ID`를 승인한다.
4. 화면명세에 `COMMON_CODE` 출처 계약을 기록한다.
5. Controller·공통코드 Service·Thymeleaf select를 연결한다.
6. POST 요청의 코드값을 서버에서 다시 검증한다.

승인 예시는 다음과 같다.

```yaml
screenName: 공지사항 등록
columnName: NOTICE_SE
javaFieldName: noticeSe
codeGroupId: COM001
required: true
defaultValue: GENERAL
includeDisabledCodes: false
```

화면명세 예시는 다음과 같다.

```json
{
  "fieldName": "noticeSe",
  "sourceType": "COMMON_CODE",
  "columnName": "NOTICE_SE",
  "codeGroupId": "COM001",
  "required": true,
  "defaultValue": "GENERAL"
}
```

### 3.3 생성 계층

```text
공통코드 Repository/Service
        ↓
Controller: model.addAttribute("noticeSeOptions", codeList)
        ↓
Thymeleaf: <select th:field="*{noticeSe}">
        ↓
등록·수정 처리: 허용 코드 재검증
```

### 3.4 구현 규칙

- DB에는 코드값을 저장하고 화면에는 코드명을 표시한다.
- 사용 중지 코드는 신규 등록 옵션에서 제외한다.
- 기존 데이터가 폐기된 코드값을 가지고 있으면 수정 화면에서 현재 값을 확인할 수 있어야 한다.
- 사용자가 임의 코드값을 POST해도 서버 허용 목록 검증을 통과해야 한다.
- 코드 그룹이 없다는 이유로 생성기가 공통코드 데이터를 자동 INSERT하지 않는다.
- 목록·상세 쿼리는 승인된 공통코드 계약을 사용하여 코드명을 projection한다.

### 3.5 완료 기준

- 승인된 `CODE_ID`가 화면명세에 기록되어 있다.
- 등록·수정 화면에 select가 표시된다.
- Controller가 옵션 목록을 전달한다.
- 목록·상세 화면에 코드값 대신 코드명이 표시된다.
- 허용되지 않은 코드값 저장이 차단된다.
- 정상 코드, 폐기 코드, 알 수 없는 코드 테스트가 통과한다.

---

## 4. 파일 업로드 정책

### 4.1 사전 결정 항목

| 구분 | 결정 내용 |
|---|---|
| 저장 위치 | 서버 파일시스템, NAS, S3 호환 스토리지 등 |
| 파일 메타데이터 | 기존 `COMTNFILE`/`COMTNFILEDETAIL` 또는 기관별 테이블 |
| 파일 크기 | 파일당 최대 크기와 요청당 총용량 |
| 파일 개수 | 게시글당 최대 첨부 개수 |
| 허용 유형 | 확장자, MIME, 파일 시그니처 |
| 악성코드 검사 | ClamAV, 백신 API, 기관 보안 솔루션 |
| 다운로드 권한 | 공개, 로그인, 작성자, 업무 권한 |
| 보존 기간 | 게시글 삭제 시 즉시 삭제 또는 유예 보관 |
| 파일명 | 원본 파일명 보존 및 저장 파일명 UUID화 |
| 암호화 | 저장·전송 암호화 기준 |
| 감사 로그 | 업로드·다운로드·삭제 사용자와 시간 |

### 4.2 권장 아키텍처

생성 코드가 특정 저장 제품에 직접 결합되지 않도록 저장소 인터페이스를 분리한다.

```text
Board Controller
      ↓
AttachmentService
      ├─ FileSystemAttachmentStorage
      ├─ ObjectStorageAttachmentStorage
      └─ 기관 전용 AttachmentStorage
      ↓
파일 메타데이터 Repository
```

```java
public interface AttachmentStorage {
    StoredFile store(UploadRequest request);
    Resource load(String storedFileName);
    void delete(String storedFileName);
}
```

### 4.3 권장 업로드 흐름

```text
multipart 요청
   ↓
파일 개수·크기 검사
   ↓
확장자·MIME·파일 시그니처 검사
   ↓
임시 저장
   ↓
악성코드 검사
   ↓
최종 저장소 이동
   ↓
COMTNFILE/COMTNFILEDETAIL 기록
   ↓
게시글 ATCH_FILE_ID 연결
```

DB 저장에 실패하면 최종 저장 파일을 제거해야 한다. 파일 저장에 실패하면 게시글의
`ATCH_FILE_ID`를 갱신하지 않아야 한다. 파일시스템과 DB는 단일 트랜잭션으로 묶을 수 없으므로
보상 처리와 실패 로그가 필요하다.

### 4.4 보안 요구사항

- 사용자 파일명을 서버 경로로 직접 사용하지 않는다.
- `../` 경로 이동과 심볼릭 링크 우회를 차단한다.
- 실행 파일과 스크립트 파일을 차단한다.
- HTML·SVG 허용 시 별도의 XSS 정책을 적용한다.
- 확장자뿐 아니라 실제 MIME과 파일 시그니처를 검사한다.
- 업로드 디렉터리를 웹 정적 리소스 경로 밖에 둔다.
- 다운로드 전에 게시글·파일 권한을 재검증한다.
- 다운로드 응답에 안전한 `Content-Disposition`을 설정한다.
- 로그에 파일 내용과 개인정보를 기록하지 않는다.
- 삭제 실패 파일을 정리하는 재처리 작업을 마련한다.

### 4.5 설정 예시

```yaml
egov:
  attachment:
    enabled: true
    storage-type: filesystem
    max-file-size: 20MB
    max-request-size: 100MB
    max-files: 5
    allowed-extensions: pdf,hwp,hwpx,docx,xlsx,png,jpg
    antivirus-required: true
```

`enabled=false`이면 다운로드 목록만 생성하고 업로드 UI와 저장 백엔드는 생성하지 않는다.

### 4.6 완료 기준

- 업로드 정책 ADR이 승인되어 있다.
- 저장소 구현체가 확정되어 있다.
- 등록·수정·삭제와 파일 생명주기가 연결되어 있다.
- 다운로드 권한 검사가 적용되어 있다.
- 크기·개수·확장자·MIME·악성코드 검사가 적용되어 있다.
- 실패 시 임시 파일과 DB가 보상 처리된다.
- 정상, 용량 초과, MIME 위장, 권한 없음, 저장 실패 테스트가 통과한다.

---

## 5. Vision provider 승인

### 5.1 지원 경로

현재 구조는 다음 provider를 지원한다.

| provider | 용도 |
|---|---|
| `disabled` | 비전 분석 비활성, 기본값 |
| `openai` | 외부 OpenAI Vision 모델 사용 |
| `ollama` | 내부 또는 로컬 멀티모달 모델 사용 |

provider는 개발자가 편의상 선택하지 않는다. 배포 기관이 이미지 외부 전송과 모델 사용을 승인해야 한다.

### 5.2 ADR 필수 항목

문서 예시 제목은 `ADR-XXX: 디자인 참조 이미지 비전 분석 Provider 결정`이다.

ADR에는 다음 내용을 기록한다.

1. 인터넷망, 업무망, 망분리 여부
2. 외부 API 호출 허용 여부
3. 화면 캡처에 포함될 수 있는 정보의 등급
4. 개인정보·내부정보 마스킹 기준
5. 요청·응답 데이터 보존 정책
6. API 키와 Secret 저장 위치
7. 승인 endpoint와 모델명
8. timeout, retry, 동시 처리 수
9. 호출량·비용·쿼터 제한
10. 장애 시 fallback과 fail-closed 정책
11. 감사 로그 항목
12. 모델·라이선스 업데이트 절차

### 5.3 환경별 선택

#### 외부 API 사용 가능

```yaml
app:
  design-vision:
    provider: openai
```

다음 조건을 추가로 충족해야 한다.

- API 키를 환경변수 또는 Secret Manager에서 주입한다.
- 이미지 원본과 base64 데이터를 로그에 남기지 않는다.
- 민감정보 마스킹 절차를 적용한다.
- 승인 모델 이름과 endpoint를 고정한다.
- 월별 비용·호출량을 제한한다.

#### 망분리 또는 외부 전송 금지

```yaml
app:
  design-vision:
    provider: ollama
```

다음 항목을 확인한다.

- 내부 추론 서버 설치 위치
- 멀티모달 모델 반입 승인
- GPU·메모리 요구량
- 동시 요청 처리량
- 모델 라이선스
- 모델 업데이트와 롤백 절차

#### 승인 전

```yaml
app:
  design-vision:
    provider: disabled
```

승인 전에는 기존 결정론적 템플릿 생성만 사용한다.

### 5.4 정확도 평가 세트

승인 후 바로 운영에 연결하지 않고 대표 자료 평가 세트를 구성한다.

| 자료 | 권장 건수 |
|---|---:|
| 게시판 목록 | 10 |
| 게시판 상세 | 10 |
| 등록·수정 폼 | 10 |
| MasterDetail | 10 |
| 이미지형 PDF | 10 |
| 손그림 | 10 |

평가 항목은 다음과 같다.

- archetype 분류 정확도
- 필드 라벨 추출 정확도
- 버튼·액션 인식 정확도
- 목록·상세·폼 구분 정확도
- 컬럼 매핑 후보 정확도
- `UNMAPPED` 처리 적절성
- 잘못된 자동 확정 비율

전체 정확도보다 잘못된 자동 확정 비율을 우선 평가한다. 모르는 항목을 `UNMAPPED`로 남기는 결과가
틀린 컬럼을 확정하는 결과보다 안전하다.

### 5.5 완료 기준

- 보안·운영 ADR이 승인되어 있다.
- 운영 provider 프로파일이 결정되어 있다.
- Secret 관리가 구성되어 있다.
- 대표 이미지 평가 세트를 실행했다.
- 오탐 기준을 충족한다.
- provider 장애 시 `disabled` 또는 수동 화면명세 경로로 전환된다.

---

## 6. 브라우저 시각 회귀

### 6.1 선행 조건

- 실제 생성된 eGovFrame 대상 프로젝트
- 실행 가능한 서버 URL
- 고정 테스트 데이터
- 브라우저 실행 환경
- 승인된 기준 이미지
- 동일한 KRDS CSS·폰트 로딩 환경

### 6.2 권장 구조

Playwright 기반 별도 테스트 프로젝트 또는 대상 프로젝트의 테스트 디렉터리를 권장한다.

```text
visual-regression/
├── fixtures/
│   └── board-data.sql
├── tests/
│   ├── crud-list.spec.ts
│   ├── crud-detail.spec.ts
│   ├── board-form.spec.ts
│   └── master-detail.spec.ts
└── snapshots/
    ├── desktop/
    ├── tablet/
    └── mobile/
```

### 6.3 기본 해상도

| 구분 | 너비 |
|---|---:|
| 모바일 | 360px |
| 태블릿 | 768px |
| 데스크톱 | 1280px |

기관 업무 PC 기준이 별도로 있으면 1440px 또는 1920px을 추가한다.

### 6.4 화면별 검사 항목

| 화면 | 주요 검사 항목 |
|---|---|
| CRUD 목록 | 검색폼, pageUnit, 테이블, 페이지네이션 |
| CRUD 상세 | 정의 테이블, 버튼 배치, 삭제 모달 |
| CRUD 등록·수정 | 필드 너비, 필수 표시, 오류 메시지 |
| Board 목록 | 공지 배지, 첨부 아이콘, empty state |
| Board 상세 | 첨부 목록, 이전글·다음글 |
| Board 폼 | textarea, 글자수, radio |
| MasterDetail 목록 | 체크박스, 상태 배지, 일괄삭제 |
| MasterDetail 상세 | 마스터 정보와 디테일 그리드 |

### 6.5 안정적인 캡처 조건

- 애니메이션과 transition을 비활성화한다.
- 현재 시간과 난수를 고정한다.
- 테스트 DB 데이터를 매 실행 전 동일하게 구성한다.
- 폰트와 네트워크 요청이 완료된 후 캡처한다.
- 사용자명·조회수 등 동적 영역을 마스킹한다.
- 토스트·모달은 각각 명시적인 상태로 캡처한다.
- OS, 브라우저, device scale factor를 CI 이미지로 고정한다.

### 6.6 기준 이미지 승인 절차

```text
첫 캡처
  ↓
디자이너·업무 담당자 검토
  ↓
승인 이미지만 baseline 등록
  ↓
PR마다 비교
  ↓
차이가 있으면 diff artifact 생성 및 실패
  ↓
의도된 변경만 baseline 재승인
```

자동 실행 결과로 기준 이미지를 무조건 덮어쓰지 않는다. 기준 이미지 변경은 코드 리뷰와 별도의
디자인 승인 대상으로 관리한다.

### 6.7 판정 규칙

- 구조 깨짐, overflow, 버튼·입력 크기 변경은 실패로 처리한다.
- 폰트 안티앨리어싱 차이는 낮은 임계치 안에서 허용한다.
- 동적 데이터 영역은 마스킹한다.
- 실패 시 원본, 현재 이미지, diff 이미지를 CI artifact로 남긴다.
- 기준 이미지 갱신은 승인자를 명시한다.

### 6.8 완료 기준

- 대상 프로젝트 경로와 실행 URL이 확정되어 있다.
- 고정 테스트 데이터가 준비되어 있다.
- 360/768/1280px 기준 이미지가 승인되어 있다.
- CI에서 자동 비교가 실행된다.
- 실패 시 diff 이미지가 저장된다.
- 기준 이미지 갱신 절차가 문서화되어 있다.

---

## 7. 역할과 승인 주체

| 작업 | 주 담당 | 승인 주체 |
|---|---|---|
| `CODE_ID` 후보 조사 | 개발자·DB 담당자 | 업무 담당자 |
| 파일 저장소 설계 | 개발자·인프라 담당자 | 보안·운영 담당자 |
| 업로드 허용 정책 | 보안 담당자 | 개인정보·보안 책임자 |
| Vision provider 검토 | AI·인프라 담당자 | 기관 보안 심의 |
| 모델 정확도 평가 | 개발자·업무 담당자 | 서비스 책임자 |
| 기준 이미지 생성 | 프론트엔드·QA | 디자이너·업무 담당자 |
| 시각 회귀 CI | 개발자·DevOps | 개발 책임자 |

## 8. 착수 입력 체크리스트

다음 정보가 확보되면 후속 구현을 시작할 수 있다.

- [ ] Board 대상 테이블명
- [ ] 카테고리 컬럼명
- [ ] 승인 공통코드 `CODE_ID`
- [ ] 필수 여부와 기본 코드값
- [ ] 파일 저장 방식
- [ ] 허용 확장자·MIME·크기·개수
- [ ] 악성코드 검사 방식
- [ ] 다운로드 권한 기준
- [ ] 파일 보존·삭제 정책
- [ ] 외부 API 허용 여부
- [ ] OpenAI 또는 Ollama 선택
- [ ] 승인 endpoint와 모델명
- [ ] Secret 관리 방식
- [ ] 시각 회귀 대상 프로젝트 경로
- [ ] 실행 URL과 테스트 계정
- [ ] 기준 이미지로 사용할 승인 Design Template

## 9. 최종 완료 판정

다음 조건을 모두 만족해야 외부 의존 잔여 항목을 완료 처리한다.

1. 공통코드 계약이 승인 화면명세에 기록되어 있다.
2. 파일 업로드 정책 ADR과 저장소 구현이 승인되어 있다.
3. Vision provider ADR과 운영 프로파일이 승인되어 있다.
4. 대표 이미지 모델 평가가 오탐 기준을 통과했다.
5. 실제 생성 프로젝트가 독립 컴파일과 기능 테스트를 통과했다.
6. 360/768/1280px 시각 회귀 기준 이미지가 승인되어 있다.
7. CI가 시각 차이와 생성 코드 품질 실패를 artifact로 남긴다.

위 조건이 충족되기 전에는 provider를 기본 활성화하거나, 파일 업로드 저장소를 추측해 생성하거나,
시각 회귀 기준 이미지를 자동 갱신하지 않는다.
