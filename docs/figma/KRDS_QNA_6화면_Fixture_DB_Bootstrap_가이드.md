# KRDS Q&A 6개 화면 Fixture·DB Bootstrap 가이드

## 1. 개요

현재 Q&A 6개 화면은 Figma Plugin이 임의로 설계하거나 사람이 매번 직접 등록하는 방식이 아니다. 프로젝트에 미리 준비된 Q&A 화면 설계도인 **Fixture**를 Spring Boot의 **Bootstrap 서비스**가 읽어 서버 DB에 `FigmaScreenSpec`으로 등록한다.

등록된 화면 명세는 서버가 KRDS Profile 및 Component Registry와 함께 Bundle로 조립하며, Figma Plugin은 그 Bundle을 받아 실제 Frame과 Component Instance를 생성한다.

```text
Q&A Fixture
→ Bootstrap 서비스
→ 서버 DB의 FigmaScreenSpec
→ 서버의 Bundle 조립
→ Figma Plugin
→ 실제 Figma 화면
```

## 2. 용어를 쉽게 이해하기

건축 과정에 비유하면 다음과 같다.

| 시스템 용어 | 쉬운 의미 | 건축 비유 |
|---|---|---|
| Fixture | 미리 준비된 기준 화면 데이터 | 원본 건축 도면 |
| Bootstrap 서비스 | Fixture와 계약을 DB에 등록하는 초기화 기능 | 도면 등록 담당자 |
| DB | 화면 명세와 버전을 보관하는 저장소 | 승인된 도면 보관함 |
| Bundle | Plugin에 전달할 계약 묶음 | 시공용 도면 패키지 |
| Figma Plugin | Bundle을 실제 Figma 노드로 구현 | 시공 담당자 |

Bootstrap 서비스는 Figma 화면을 직접 그리지 않는다. 화면 설계도를 DB에 준비해 두고, Plugin이 나중에 서버로부터 그 설계도를 받아 화면을 생성하도록 한다.

## 3. Q&A 6개 화면

현재 준비된 Q&A 화면은 다음과 같다.

| Screen ID | 용도 |
|---|---|
| `qna-list` | 질문 목록 |
| `qna-create` | 질문 등록 |
| `qna-detail` | 질문 상세 |
| `qna-answer-list` | 답변 관리 목록 |
| `qna-answer-detail` | 답변 상세 |
| `qna-answer-create` | 답변 등록 |

Fixture에는 화면 ID와 이름뿐 아니라 다음 정보가 포함된다.

- 화면 유형과 버전
- Page·Section·Component의 계층 구조
- 각 노드의 Semantic Role
- 사용할 KRDS Component와 Variant
- Component Property
- 부모·자식 관계와 Slot 순서
- 화면 승인 상태
- Design System Profile과 Registry 버전

## 4. Q&A 목록 화면의 구조

`qna-list`와 `qna-answer-list`는 목록 화면이므로 다음과 같은 공통 구조를 사용한다.

```text
ListPage
├── PageHeader
├── SearchPanel
├── DataTable
│   ├── Header
│   │   └── Cell × 6
│   ├── Sample Row 1
│   │   └── Cell × 6
│   ├── Sample Row 2
│   │   └── Cell × 6
│   └── Sample Row 3
│       └── Cell × 6
├── Pagination
└── ActionArea
    └── 등록 버튼
```

`qna-list v3`의 기본 컬럼은 다음과 같다.

1. 번호
2. 제목
3. 작성자
4. 등록일
5. 처리상태
6. 답변상태

목록의 샘플 행은 Figma Preview와 레이아웃 검증을 위한 예제 데이터다. 실제 운영 Q&A 게시글을 조회한 결과가 아니다.

## 5. Fixture 원본 위치

Q&A Fixture 원본은 다음 디렉터리에서 관리한다.

```text
website-figma-contract/fixtures/qna/
```

Fixture JSON은 개발자와 테스트 환경에서 동일한 화면을 반복 재현하기 위한 기준 입력이다.

```text
프로젝트의 Fixture JSON
= 변경 이력으로 관리되는 원본 설계도

DB의 FigmaScreenSpec
= 실행 중인 서버가 사용하는 등록된 설계도
```

Fixture 파일을 수정하는 것만으로 기존 DB 내용이 즉시 변경되지는 않는다. 변경된 Fixture를 DB에 반영하려면 Bootstrap을 다시 실행해야 한다.

## 6. Bootstrap 서비스

Q&A Bootstrap의 핵심 구현은 다음 서비스다.

```text
src/main/java/com/krdevops/springai/service/designsystem/
KrdsQnaFixtureBootstrapService.java
```

이 서비스는 다음 순서로 동작한다.

### 6.1 KRDS 계약 읽기

먼저 다음 계약을 준비한다.

- KRDS Design System Profile
- Component Registry v2
- Variant Rule Set
- Screen Pattern
- Figma Library Inventory Snapshot
- Q&A 6개 Screen Fixture

각 계약의 역할은 다음과 같다.

| 계약 | 역할 |
|---|---|
| Design System Profile | 사용할 KRDS 버전과 Registry 버전 지정 |
| Component Registry | 논리 Component와 실제 Figma Published Component 연결 |
| Variant Rule Set | 화면 문맥과 Role에 맞는 Variant 결정 규칙 |
| Screen Pattern | 목록·상세·등록 화면의 허용 구조와 순서 정의 |
| Library Inventory | 실제 Figma Library에 존재하는 Component·Property 확인 |
| Screen Fixture | Q&A 화면별 실제 노드 구조와 속성 정의 |

### 6.2 계약 버전 확인

Profile, Registry, Rule Set과 화면 명세가 서로 호환되는 버전을 참조하는지 확인한다. 예시는 다음과 같다.

```text
Profile ID: krds
Profile Version: 2.0.0
Registry Version: 2.1.0
Component Contract Version: 2.1.0
Variant Rule Set Version: 2.0.0-candidate
```

버전이 맞지 않거나 필요한 Registry가 없으면 정상적인 Component Resolution 및 Bundle 생성이 불가능하다.

### 6.3 Fixture JSON 변환

Q&A Fixture JSON 6개를 읽어 Java의 `FigmaScreenSpec` 객체로 변환한다.

```text
Q&A JSON Fixture
→ Jackson ObjectMapper
→ FigmaScreenSpec
```

### 6.4 목록 Fixture 보완

목록 화면인 `qna-list`와 `qna-answer-list`는 Bootstrap 과정에서 다음 품질 규칙을 적용한다.

- `krds.dataTable.v1` Layout Recipe
- Header 1개
- 샘플 Row 3개
- 각 행의 Cell 6개
- SearchPanel 콘텐츠 폭 적용
- Pagination을 Table 하단에 배치
- 등록 버튼을 Action Area 우측에 배치
- 콘텐츠 최대 폭 `1280px`
- Section 간격 `40px`

이 보완 로직은 `KrdsQnaFixtureBootstrapService.enrichListFixture()`에서 수행한다. 현재 보완된 목록 화면은 `screenVersion: 3`으로 저장된다.

### 6.5 승인 상태 확정

Figma Plugin에서 실제 Apply가 가능하도록 Screen Spec을 `APPROVED` 상태로 준비한다.

```json
{
  "status": "APPROVED"
}
```

### 6.6 DB 저장

준비된 `FigmaScreenSpec` 6개를 `FigmaScreenSpecRepository`를 통해 DB에 저장한다.

화면은 `screenId + screenVersion` 조합으로 구분된다. 따라서 과거 버전과 최신 버전을 함께 보관할 수 있다.

```text
qna-list v2 = 과거 목록 구조
qna-list v3 = 6열·3행·전체 폭을 적용한 현재 구조
```

Plugin에서 Version을 입력하지 않으면 서버가 해당 Screen ID의 최신 버전을 조회한다.

## 7. Bootstrap 실행 조건

서버 시작 시 다음 설정이 활성화되어 있으면 Bootstrap이 실행된다.

```text
APP_FIGMA_CONTRACT_BOOTSTRAP_ENABLED=true
```

전체 실행 순서는 다음과 같다.

```text
Spring Boot 시작
→ Bootstrap 활성화 여부 확인
→ KRDS 계약 파일 읽기
→ Profile·Registry·Rule·Pattern·Inventory 등록
→ Q&A Fixture 6개 읽기
→ 목록 Fixture v3 보완
→ Q&A FigmaScreenSpec 6개 DB 저장
→ Bundle 다운로드 API 제공
```

설정이 비활성화되어 있으면 서버 시작 시 Fixture를 자동으로 DB에 등록하지 않는다.

## 8. 중복과 버전 관리

Profile, Registry, Rule Set 등의 계약은 버전별 불변 데이터로 관리하는 것이 기본 원칙이다.

```text
같은 버전·같은 내용
→ 기존 데이터 재사용 또는 중복 방지

같은 버전·다른 내용
→ 버전 충돌 처리

새 버전
→ 새 버전으로 별도 저장
```

화면 명세도 기존 버전을 무조건 덮어쓰지 않고 화면 ID와 버전으로 구분한다. 이렇게 해야 이전 결과를 재현하고 v2와 v3의 변경 사항을 비교할 수 있다.

## 9. DB 등록 이후 Bundle 생성

DB 등록이 완료됐다고 해서 Figma 화면이 자동으로 생성되는 것은 아니다. 사용자가 Plugin에서 Bundle을 가져오고 Apply해야 한다.

```text
1. Plugin에 qna-list 입력
2. 서버에서 Bundle 가져오기 실행
3. 서버가 DB에서 최신 qna-list v3 조회
4. Spec이 참조한 정확한 Profile 버전 조회
5. Spec이 참조한 정확한 Registry 버전 조회
6. FigmaExportBundle JSON 조립
7. Plugin Preview 표시
8. 사용자가 REPLACE 또는 MERGE 실행
9. Plugin이 실제 Figma Frame과 Instance 생성
```

최신 Bundle 조회 API는 다음과 같다.

```http
GET /api/figma/screens/qna-list/download
```

특정 버전을 지정하려면 다음과 같이 요청한다.

```http
GET /api/figma/screens/qna-list/download?version=3
```

## 10. 역할과 책임

| 구성요소 | 책임 |
|---|---|
| Q&A Fixture | 반복 재현 가능한 Q&A 기준 설계도 제공 |
| Bootstrap 서비스 | Fixture와 KRDS 계약을 DB에 등록 |
| DB | 화면·계약의 버전별 보관 |
| Component Resolver | Role을 실제 Component와 Variant로 해석 |
| Bundle 서비스 | Screen Spec, Profile, Registry를 JSON으로 조립 |
| Figma Plugin | Bundle을 실제 Frame과 Published Instance로 생성 |
| 사용자 | Preview 확인 후 Apply 승인 |

## 11. Fixture와 운영 데이터의 차이

Fixture에 포함된 샘플 행은 Figma UI 구조와 레이아웃을 검증하기 위한 데이터다.

```text
Fixture 샘플 데이터
→ Figma 설계·Preview·회귀 테스트용

실제 Q&A 운영 데이터
→ 실행 중인 웹 애플리케이션의 업무 목록 조회용
```

따라서 Fixture에 있는 질문 제목, 작성자, 날짜와 상태는 운영 Q&A DB의 실제 게시물이 아니다.

## 12. Fixture와 Bootstrap을 사용하는 이유

- 개발자마다 동일한 기준 화면 사용
- 테스트 DB를 빠르게 준비
- Q&A 6개 화면을 반복 재현
- Plugin 동작 회귀 검증
- Component Registry 및 Variant Rule 검증
- 오류 발생 시 동일한 입력으로 재현
- 화면 버전 간 결과 비교
- Figma 화면을 매번 수작업으로 등록하는 비용 제거

## 13. 핵심 정리

Q&A Fixture는 6개 화면의 미리 준비된 설계도다. Bootstrap 서비스는 서버 시작 시 이 설계도와 KRDS 계약을 읽고 버전별 `FigmaScreenSpec`으로 DB에 등록한다. 서버는 등록된 Spec과 정확히 일치하는 Profile·Registry 버전을 Bundle로 묶고, Figma Plugin은 사용자의 Apply 명령에 따라 실제 Figma 화면을 생성한다.

```text
Fixture는 설계도,
Bootstrap은 등록 작업,
DB는 보관소,
Bundle은 전달 패키지,
Plugin은 실제 화면 생성기다.
```
