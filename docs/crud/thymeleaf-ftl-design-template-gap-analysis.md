
# Thymeleaf FTL ↔ Design Templates 구조 Gap 분석

> **최종 갱신:** 2026-07-01 — 현재 코드 기준 재작성.

---

## Design Templates 원본 위치

```
/Users/jeongdaeseob/Downloads/KRDS Design System/templates/
├── ftc-list/FtcList.dc.html                 ← CRUD 일반형 목록
├── ftc-detail/FtcDetail.dc.html             ← CRUD 일반형 상세
├── ftc-board/FtcBoard.dc.html               ← Board 목록형
├── ftc-board-detail/FtcBoardDetail.dc.html  ← Board 상세형
├── ftc-board-form/FtcBoardForm.dc.html      ← Board 등록/수정형
├── ftc-crud-master/FtcCrudMaster.dc.html    ← MasterDetail 목록형
└── ftc-crud-detail/FtcCrudDetail.dc.html    ← MasterDetail 상세형
```

CRUD 등록/수정(regist/updt)에 직접 대응하는 Design Template은 존재하지 않는다.
본 분석은 위 7개 파일과 현재 FTL을 직접 읽어 비교한 결과다.

### 현재 FTL 공통 특성

현재 Thymeleaf FTL(화면 11개 + 레이아웃 2개)의 스타일 방식:

| 요소 | 현재 방식 |
|---|---|
| GNB | `krds-main-menu` KRDS 공식 클래스 |
| LNB | `data-layout-sidebar` + inline style |
| Footer | inline style |
| Breadcrumb | `krds-breadcrumb-wrap` KRDS 클래스 |
| Page title + 버튼 | inline style div + `krds-btn` |
| Alert (flash) | inline style div + `role="alert"` |
| 검색 박스 | inline style 컨테이너 + `krds-form-select`·`krds-input`·`krds-btn` |
| 행 클릭 | `data-row-link="true"` 속성 + JS selector |
| Table | `krds-table-wrap`, `tbl col` |
| Pagination | `krds-pagination` |
| 버튼 | `krds-btn primary/secondary/negative/small/medium` |
| Form input | `krds-input`, `krds-form-select` |

`egov-*` 클래스는 레이아웃·화면 FTL 전체에서 0건이다.

### 레이아웃 상태

두 레이아웃 파일(`crud/layout/default.html.ftl`, `masterdetail/layout/default.html.ftl`)은
내용이 동일하다. 두 파일 모두:

- `styles.css` 링크
- `krds-main-menu` GNB (generic 4개 항목)
- `data-layout-sidebar` LNB ("업무관리", 4개 링크)
- inline styled Footer
- `krds.min.js` 링크

Design Templates는 `GnbNav.dc.html` 별도 컴포넌트(조직별 메뉴), "소식·뉴스" LNB 아코디언, 상세 Footer를 사용한다.

---

## 템플릿 대응 관계

| Design Template | 대응 FTL 파일 | 레이아웃 |
|---|---|---|
| `FtcList.dc.html` | `crud/thymeleaf-list.html.ftl` | crud/layout (= masterdetail/layout) |
| `FtcDetail.dc.html` | `crud/thymeleaf-detail.html.ftl` | crud/layout |
| 대응 없음 | `crud/thymeleaf-regist.html.ftl` | crud/layout |
| 대응 없음 | `crud/thymeleaf-updt.html.ftl` | crud/layout |
| `FtcBoard.dc.html` | `board/thymeleaf-list.html.ftl` | crud/layout 공유 |
| `FtcBoardDetail.dc.html` | `board/thymeleaf-detail.html.ftl` | crud/layout 공유 |
| `FtcBoardForm.dc.html` | `board/thymeleaf-regist.html.ftl`, `board/thymeleaf-updt.html.ftl` | crud/layout 공유 |
| `FtcCrudMaster.dc.html` | `masterdetail/thymeleaf-list.html.ftl` | masterdetail/layout (= crud/layout) |
| `FtcCrudDetail.dc.html` | `masterdetail/thymeleaf-detail.html.ftl` | masterdetail/layout |

---

## 템플릿별 구조 Gap 분석

범례: ✅ 일치 또는 동등 · △ 부분 구현 또는 현재 미구현(마크업 이식/생성 규칙 확장 가능) · ❌ 현재 범위상 직접 대응 어려움

### FtcList.dc.html vs crud/thymeleaf-list.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` (조직별 메뉴) | △ `krds-main-menu` generic 4항목 |
| LNB | "소식·뉴스" 아코디언 메뉴 | △ `data-layout-sidebar` "업무관리" generic |
| 브레드크럼 | 텍스트 링크 | △ `krds-breadcrumb-wrap` (SVG 아이콘 없음) |
| 페이지 타이틀 | h1 + URL복사/프린트 버튼 | △ h1 + 등록 버튼, URL복사·프린트 없음 |
| 검색 박스 | 구분 탭 + select + input + 기간 설정 | △ select + input + 검색/초기화, 구분 탭·기간 설정 없음 |
| 건수 + 페이지당 건수 select | 총건수 + "10개/20개/30개/50개" | △ 총건수만, select 없음 |
| 테이블 컬럼 | 번호/구분배지/제목/담당부서/등록일/첨부아이콘 | △ listFields 루프로 유동적 |
| 구분(카테고리) 배지 | 파란 pill 배지 | △ 현재 미구현 (템플릿 마크업 이식 가능, 카테고리 데이터 필요) |
| 첨부 아이콘 컬럼 | 다운로드 SVG 아이콘 | △ 현재 미구현 (템플릿 마크업 이식 가능, 첨부 존재 데이터 필요) |
| Empty State | 텍스트 메시지 | △ 텍스트만 |
| 페이지네이션 | 숫자 버튼 직접 구조 (… 포함) | △ `krds-pagination` 구조 다름 |
| Footer | 상세 Footer | △ inline styled generic footer |

### FtcDetail.dc.html vs crud/thymeleaf-detail.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` | △ `krds-main-menu` generic |
| LNB | "소식·뉴스" 메뉴 | △ `data-layout-sidebar` generic |
| 페이지 타이틀 | h1 + URL복사/프린트 버튼 | △ h1 + 목록 버튼, URL복사·프린트 없음 |
| 게시물 제목 + 메타 바 | h2 제목 + 담당부서/등록일/조회수 가로 바 | △ fields 루프, 구조 다름 |
| 첨부파일 | 파일명·크기 다운로드 링크 | △ 현재 미구현 (파일 메타데이터/다운로드 URL 필요) |
| 이전글/다음글 네비게이션 | 이전글·다음글 링크 블록 | △ 현재 미구현 (이전/다음 데이터 조회 로직 필요) |
| 하단 버튼 | 목록으로 (중앙) | △ 목록·삭제 버튼, 배치 다름 |
| Footer | 상세 Footer | △ inline styled generic footer |

### CRUD regist / updt — Design Template 대응 없음

`crud/thymeleaf-regist.html.ftl`, `crud/thymeleaf-updt.html.ftl`에 직접 대응하는
Design Template이 존재하지 않는다.

### FtcBoard.dc.html vs board/thymeleaf-list.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` | △ `krds-main-menu` generic |
| LNB | "소식·뉴스" 카테고리 아코디언 | △ `data-layout-sidebar` generic |
| 브레드크럼 | 텍스트 링크 | △ `krds-breadcrumb-wrap` |
| 페이지 타이틀 | h1 + URL복사/프린트 버튼 | △ h1 + 등록 버튼, URL복사·프린트 없음 |
| 검색 박스 | select + input + 검색/초기화 | △ `krds-form-select` + `krds-input` + 버튼 |
| 건수 + 페이지당 건수 select | 총건수 + "10개/20개/30개" | △ 총건수만, select 없음 |
| 공지 고정 행 | 파란 "공지" 배지 행 별도 렌더 | △ 현재 미구현 (공지 여부 데이터와 별도 렌더 규칙 필요) |
| 첨부 아이콘 컬럼 | 다운로드 SVG 아이콘 컬럼 | △ 현재 미구현 (템플릿 마크업 이식 가능, 첨부 존재 데이터 필요) |
| Empty State | 텍스트 메시지 | △ 텍스트만 |
| 페이지네이션 | 숫자 버튼 직접 구조 | △ `krds-pagination` 구조 다름 |
| Footer | 상세 Footer | △ inline styled generic footer |

### FtcBoardDetail.dc.html vs board/thymeleaf-detail.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` | △ `krds-main-menu` generic |
| LNB | "소식·뉴스" 카테고리 메뉴 | △ `data-layout-sidebar` generic |
| 브레드크럼 | SVG 홈 아이콘 + 링크 | △ `krds-breadcrumb-wrap`, SVG 없음 |
| 페이지 타이틀 | h1 + URL복사/프린트 + 목록/수정 버튼 | △ h1 + 목록·수정 버튼, URL복사·프린트 없음 |
| 게시글 정보 테이블 | 4컬럼 정의 테이블 (번호·카테고리, 제목, 작성자·담당부서, 등록일·조회수) | △ fields 루프, 컬럼 구조 다름 |
| 첨부파일 행 | 파일명·용량 다운로드 링크 | △ `atchFileId`만, 실제 파일 링크 없음 |
| 이전글/다음글 네비게이션 | 이전글·다음글 링크 블록 | △ 현재 미구현 (이전/다음 데이터 조회 로직 필요) |
| 하단 버튼 | 목록(좌) / 수정·삭제(우) | △ 배치 다름 |

### FtcBoardForm.dc.html vs board/thymeleaf-regist.html.ftl / thymeleaf-updt.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` | △ `krds-main-menu` generic |
| LNB | "소식·뉴스" 카테고리 메뉴 | △ `data-layout-sidebar` generic |
| 카테고리 select | 카테고리 + 유효성 오류 메시지 | △ 현재 미구현 (formFields 확장 또는 Board 전용 필드 규칙 필요) |
| 공개여부 radio | 공개/비공개 라디오 | △ 현재 미구현 (Board 전용 필드 규칙 필요) |
| 내용 textarea | 대형 textarea + 글자수 카운터 | △ 현재 미구현 (`textarea` 타입/글자수 스크립트 규칙 추가 필요) |
| 첨부파일 드래그 업로드 | 드래그존 + 파일 목록 + 삭제 | △ 현재 미구현 (파일 업로드 컴포넌트와 백엔드 처리 필요) |
| 저장 성공 토스트 | fixed 위치 토스트 알림 | △ 현재 미구현 (토스트 UI/스크립트 추가 가능) |
| 버튼 배치 | 취소·저장 중앙 정렬 | △ `krds-btn` 우측 정렬 |

### FtcCrudMaster.dc.html vs masterdetail/thymeleaf-list.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` | △ `krds-main-menu` generic |
| LNB | 업무 메뉴 | △ `data-layout-sidebar` generic |
| 체크박스 컬럼 + 전체 선택 | 행마다 체크박스, 헤더 전체 선택 | △ 현재 미구현 (선택 상태 관리와 일괄 동작 스크립트 필요) |
| 상태 배지 컬럼 | 사용/중지 pill 배지 | △ 현재 미구현 (상태값 매핑 규칙 필요) |
| 페이지당 건수 select | "10개/20개/50개" | △ 현재 미구현 (pageUnit 바인딩/UI 추가 가능) |
| 선택 삭제(일괄) | 체크된 행 선택삭제 버튼 | △ 현재 미구현 (일괄삭제 엔드포인트/스크립트 필요) |
| 행 수정·삭제 버튼 | 행마다 수정·삭제 인라인 버튼 | △ 수정·삭제 있음, 아이콘·스타일 다름 |
| Empty State | SVG 아이콘 + 메시지 | △ 텍스트만 |
| 삭제 확인 모달 | 모달 다이얼로그 | △ `confirm()` 팝업 |
| 삭제 완료 토스트 | fixed 토스트 알림 | △ 현재 미구현 (토스트 UI/스크립트 추가 가능) |

### FtcCrudDetail.dc.html vs masterdetail/thymeleaf-detail.html.ftl

| 구조 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` | △ `krds-main-menu` generic |
| LNB | 업무 메뉴 | △ `data-layout-sidebar` generic |
| 마스터 정보 테이블 | 4컬럼 구조 (코드·상태, 명칭, 등록일·수정일, 등록자·담당부서, 설명) | △ fields 루프, 컬럼 구조 다름 |
| 상태 배지 | 사용/중지 pill 배지 | △ 현재 미구현 (상태값 매핑 규칙 필요) |
| 디테일 목록 컬럼 | 번호·코드·명칭·순서·상태·등록일·관리 (고정 7컬럼) | △ detailFields 루프, 컬럼 유동적 |
| 디테일 행 상태 배지 | 사용/중지 pill 배지 per row | △ 현재 미구현 (상태값 매핑 규칙 필요) |
| 디테일 총건수 표시 | "총 N건" | △ 현재 미구현 (집계값 렌더링 추가 가능) |
| 삭제 확인 모달 | 모달 다이얼로그 (마스터/디테일 각각) | △ `confirm()` 팝업 |
| 토스트 알림 | fixed 토스트 | △ 현재 미구현 (토스트 UI/스크립트 추가 가능) |
| 하단 버튼 배치 | 목록(좌) / 수정·삭제(우) | △ 배치 다름 |

---

## 결론: 현재 생성 결과만으로는 Design Templates 100% 동일 구현 아님

### 이유 1 — GNB / LNB / Footer가 generic 구조

| 요소 | Design Template | FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` (조직별 메뉴 컴포넌트) | `krds-main-menu` + generic 4항목 |
| LNB | 업무별 아코디언 메뉴 (예: "소식·뉴스") | `data-layout-sidebar` + generic "업무관리" |
| Footer | 상세 조직 정보·링크 | inline styled generic footer |

두 레이아웃이 동일 파일을 사용하므로 CRUD·Board·MasterDetail 모두 같은 GNB/LNB/Footer를 공유한다.

### 이유 2 — Board 전용 요소가 아직 생성 규칙에 반영되지 않음

| 항목 | Design Template | FTL |
|---|---|---|
| 공지 고정 행 | 파란 배지 행 별도 렌더 | 현재 미구현 (규칙 추가 가능) |
| 첨부 아이콘 컬럼 | 다운로드 SVG 아이콘 | 현재 미구현 (마크업 이식 가능) |
| 이전글/다음글 네비게이션 | 상세 하단 | 현재 미구현 (조회 로직 필요) |
| 내용 textarea | 게시글 본문 편집 | 현재는 `<input type="text">` 위주 |
| 파일 업로드 UI | 드래그 존 + 파일 목록 | 현재 미구현 (업로드 처리 필요) |

### 이유 3 — MasterDetail 상호작용이 아직 생성 규칙에 반영되지 않음

| 항목 | Design Template | FTL |
|---|---|---|
| 체크박스 + 일괄삭제 | 전체 선택 + 선택삭제 버튼 | 현재 미구현 (스크립트/엔드포인트 필요) |
| 상태 배지 | 사용/중지 pill | 현재 미구현 (상태값 매핑 필요) |
| 삭제 확인 모달 | 모달 다이얼로그 | `confirm()` 팝업 |
| 삭제·저장 토스트 | fixed 화면 알림 | 현재는 페이지 리다이렉트 |

### 이유 4 — 스타일링 방식 차이

Design Templates는 인라인 스타일 기반이다.
현재 FTL은 KRDS 공식 클래스(`krds-btn`, `tbl col` 등) + inline style 혼합이다.
페이지 타이틀 배치, 버튼 정렬, LNB 아코디언 등 세부 레이아웃에서 차이가 있다.

---

## 현재 FTL 전환 상태

| 항목 | 상태 |
|---|---|
| 레이아웃 2개 `styles.css` 링크 | ✅ 완료 |
| 레이아웃 2개 `krds-main-menu` GNB | ✅ 완료 |
| 레이아웃 2개 LNB + Footer | ✅ 완료 (두 파일 동일) |
| 화면 FTL 11개 `egov-*` 클래스 제거 | ✅ 완료 (0건) |
| `krds-breadcrumb-wrap` 전환 | ✅ 완료 |
| `krds-btn` / `krds-input` / `krds-form-select` / `krds-pagination` | ✅ 완료 |
| `data-row-link` 행 클릭 | ✅ 완료 |
| Board 공지 고정 행 / 첨부 아이콘 컬럼 | △ 현재 미구현 |
| Board textarea / 파일 업로드 | △ 현재 미구현 |
| 이전글/다음글 네비게이션 | △ 현재 미구현 |
| MasterDetail 체크박스·일괄삭제·상태 배지 | △ 현재 미구현 |
| 삭제 확인 모달 (전체) | △ `confirm()` 팝업 |
| 저장·삭제 토스트 알림 (전체) | △ 현재는 리다이렉트 방식 |
| GNB/LNB 업무별 메뉴 | △ generic "업무관리" |

---

## 미구현 항목 구현 대체방안

미구현 항목은 모두 같은 성격이 아니다.
Design Template 마크업만 이식하면 되는 항목, 생성 모델에 필드 후보를 추가해야 하는 항목,
서비스/매퍼/컨트롤러까지 확장해야 하는 항목으로 나눠서 접근하는 것이 현실적이다.

### 우선순위 A — FTL 중심으로 바로 반영 가능한 항목

| 항목 | 대체 구현안 | 선행 조건 | 대상 파일 |
|---|---|---|---|
| 구분(카테고리) 배지 | `listFields` 중 카테고리 후보 필드가 있으면 Design Template의 파란 pill 마크업으로 렌더링 | 카테고리 필드 후보 탐지 규칙 (`category`, `ctgry`, `bbsId`, `noticeAt` 등) | `crud/thymeleaf-list.html.ftl`, `board/thymeleaf-list.html.ftl` |
| 첨부 아이콘 컬럼 | `hasFile && atchFileId != null`이면 첨부 컬럼을 추가하고 `atchFileId` 값 존재 시 다운로드 SVG 아이콘 표시 | Board는 기존 `BoardTemplateModel.hasFile`, `atchFileId` 사용 가능 | `board/thymeleaf-list.html.ftl` |
| Empty State SVG | Design Template의 empty 아이콘/메시지 블록을 FTL로 이식 | 데이터 로직 불필요 | CRUD/Board/MasterDetail list FTL |
| 버튼 배치 | 목록/수정/삭제 버튼 영역을 Design Template처럼 좌우 또는 중앙 배치 | 데이터 로직 불필요 | detail/regist/updt FTL |
| 페이지당 건수 select UI | `searchVO.pageUnit`에 바인딩하는 select를 추가 | 컨트롤러가 요청 pageUnit을 덮어쓰지 않도록 조정 필요 가능 | list FTL + controller FTL |

이 그룹은 “FTL로 불가”가 아니다.
마크업 이식과 FreeMarker 조건 분기만으로 대부분 처리 가능하다.

### 우선순위 B — 생성 모델 확장이 필요한 항목

| 항목 | 대체 구현안 | 필요한 모델 확장 | 이유 |
|---|---|---|---|
| Board 공지 고정 행 | `NOTICE_AT = 'Y'` 또는 공지 후보 값이면 상단 고정 행 스타일로 렌더링 | `noticeField`, `hasNoticeField` 또는 기존 `noticeAtExists` 활용 강화 | 현재 `noticeAtExists`는 ORDER BY에만 쓰임 |
| Board 카테고리 select | Board 전용 카테고리 후보 필드를 `formFields`와 별도로 렌더링 | `categoryField` 후보 탐지 | 현재 `formFields` 루프는 모든 필드를 같은 input으로 렌더링 |
| 공개여부 radio | `PUBLIC_AT`, `USE_AT`, `SECRET_AT` 등 후보 필드를 radio로 렌더링 | `visibilityField` 후보 탐지 | 필드 의미를 알아야 radio UI로 바꿀 수 있음 |
| 내용 textarea | `NTT_CN`, `content`, `description`, CLOB/TEXT 타입 필드는 textarea로 렌더링 | `FieldModel`에 `textareaCandidate` 또는 타입 기반 helper 추가 | 현재 FTL은 문자열 필드도 전부 text input |
| 상태 배지 | `USE_AT`, `STATUS`, `STTUS`, `ACTIVE_YN` 후보 필드를 pill 배지로 렌더링 | `statusField`, 상태값 라벨/색상 매핑 | MasterDetail 현재 모델에는 상태 필드 개념이 없음 |
| 디테일 총건수 표시 | `${detailList.size()}` 또는 별도 count 값을 표시 | 모델 추가 없이 가능하나 명확한 변수명 권장 | 현재 detail 화면은 목록만 렌더링 |

이 그룹은 Design Template을 그대로 가져와도, 어떤 DB 컬럼을 어떤 UI 의미로 볼지 결정해야 한다.
따라서 `BoardModelFactory`, `CrudModelFactory`, `MasterDetailTemplateModel` 쪽에 후보 필드 탐지 결과를 추가하는 방식이 적절하다.

### 우선순위 C — 백엔드/쿼리 확장이 필요한 항목

| 항목 | 대체 구현안 | 필요한 확장 | 비고 |
|---|---|---|---|
| 첨부파일 다운로드 링크 | `COMTNFILEDETAIL` 조회 결과를 `fileList`로 모델에 담고 파일명/크기/다운로드 링크 렌더링 | Mapper select, Service 메서드, Controller model attribute, 다운로드 엔드포인트 | 현재 Board는 `atchFileId`만 표시 가능 |
| 파일 업로드 UI | Design Template의 드래그존/파일 목록 마크업을 multipart form으로 변환 | multipart 설정, 업로드 Controller, 파일 저장/DB insert, 기존 파일 삭제 처리 | FTL 이식만으로는 동작 불가 |
| 이전글/다음글 네비게이션 | 현재 게시글 기준 이전/다음 게시글을 조회해 `prevPost`, `nextPost`로 렌더링 | Mapper prev/next select, Service, Controller model attribute | 정렬 기준은 `NTT_ID` 또는 `SORT_ORDR`로 결정 필요 |
| MasterDetail 선택 삭제 | 체크박스 선택값을 배열로 전송하고 일괄 삭제 처리 | bulk delete Controller, Service, Mapper, CSRF/권한 고려 | 화면 스크립트만으로는 불완전 |
| 삭제 확인 모달 | `confirm()` 대신 Design Template 모달을 이식하고 submit target을 동적으로 설정 | FTL + JS로 가능, 서버 확장은 불필요 | 접근성 속성/포커스 복귀 처리 필요 |
| 저장·삭제 토스트 | redirect 후 flash message가 있으면 토스트로 표시 | 기존 message flash 사용 가능, 필요 시 redirect attributes 정리 | 서버 변경 최소화 가능 |

이 그룹은 “디자인 마크업 이식”과 “실제 기능 구현”을 분리해서 봐야 한다.
화면만 비슷하게 만들 수는 있지만, 100% 동작까지 맞추려면 생성되는 Java/Mapper 계층도 같이 바뀌어야 한다.

### 난이도별 처리 단계

| 항목 | 구현 난이도 | 처리 단계 |
|---|---|---|
| Board 공지 배지/고정 행 | 낮음 (FTL + 기존 `noticeAtExists` 활용) | Phase 0 |
| Board 첨부 아이콘 컬럼 | 낮음 (FTL + 기존 `hasFile`, `atchFileId` 활용) | Phase 0 |
| Board `NTT_CN` textarea | 낮음~중간 (FTL 조건 분기 + 필드 후보 규칙) | Phase 0 |
| Board 상세 첨부파일 영역 구조 | 낮음 (현재 `atchFileId` 기반 표시 구조 개선) | Phase 0 |
| Empty State SVG / 버튼 배치 | 낮음 (마크업 이식) | Phase 0 |
| Flash message 토스트 | 낮음~중간 (FTL + JS) | Phase 0 |
| 페이지당 건수 select | 중간 (FTL + Controller pageUnit 처리) | Phase 1 |
| Board 이전글/다음글 | 중간 (Mapper + Service + Controller + FTL) | Phase 1 |
| Board 실제 다운로드 목록 | 중간~높음 (COMTNFILEDETAIL 조회 + 다운로드 URL) | Phase 1 |
| MasterDetail 상태 배지 | 중간 (상태 필드 후보 탐지 + FTL) | Phase 1 |
| MasterDetail 체크박스 UI | 중간 (FTL + JS) | Phase 1 |
| MasterDetail 일괄삭제 | 중간 (SQL + Controller + FTL) | Phase 1 |
| 삭제 확인 모달 | 중간 (FTL + JS, 접근성 처리) | Phase 1 |
| Board 파일 업로드 | 높음 (eGovFrame 공통 컴포넌트/파일 테이블 연동) | Phase 2 |
| GNB/LNB 업무별 메뉴 | 높음 | 생성 후 수동 수정 |

Phase 0은 현재 생성 구조를 크게 바꾸지 않고 Design Template 체감 차이를 줄이는 범위다.
Phase 1은 생성되는 Java/Mapper 또는 모델 후보 규칙까지 같이 바꾸는 범위다.
Phase 2는 파일 저장 정책, 공통 컴포넌트, 운영 환경 설정까지 영향을 주므로 별도 설계가 필요하다.

### 권장 구현 순서

1. Board 목록: 공지 배지, 첨부 아이콘 컬럼, empty state SVG를 먼저 반영한다.
2. Board 상세: `atchFileId` 표시를 파일 목록 영역 구조로 바꾸고, 실제 `fileList` 연동은 후속으로 분리한다.
3. Board 폼: `NTT_CN` textarea, `USE_AT`/공개여부 radio, 카테고리 select 후보 필드 렌더링을 추가한다.
4. 공통 list: 페이지당 건수 select와 버튼/페이지 타이틀 배치를 Design Template에 맞춘다.
5. MasterDetail 목록/상세: 상태 필드 후보 탐지 후 상태 배지와 체크박스 UI를 추가한다.
6. 기능 확장: 파일 업로드, 이전글/다음글, 일괄삭제, 모달, 토스트를 Java/Mapper/JS 단위로 구현한다.
7. 레이아웃: GNB/LNB/Footer를 업무별 Design Template 구조로 분리한다.

### 최소 변경안

가장 적은 변경으로 Design Template에 가까워지는 조합은 다음이다.

| 범위 | 변경 내용 | 효과 |
|---|---|---|
| Board 목록 FTL | `NOTICE_AT` 배지, 첨부 아이콘 컬럼, 공지 행 강조 | 시각적 차이가 가장 큰 목록 영역 개선 |
| Board 폼 FTL | `NTT_CN` textarea 렌더링 | 게시판 폼의 핵심 UX 개선 |
| Board 상세 FTL | 첨부파일 영역을 Design Template 구조로 변경 | 현재 `atchFileId` 단순 표시보다 원본에 가까움 |
| MasterDetail FTL | 상태 후보 필드가 있으면 pill 배지 표시 | 데이터 의미를 해치지 않고 시각 개선 가능 |
| 공통 FTL | 토스트를 flash message 기반으로 표시 | 서버 로직 변경 최소 |

이 최소 변경안은 DB 스키마와 생성 Java 계층을 크게 흔들지 않는다.
다만 파일 업로드, 실제 다운로드, 이전/다음 글, 일괄삭제는 별도 기능 확장으로 남는다.

---

## 완료 이력

| 항목 | 완료 시점 |
|---|---|
| CRUD 4개 FTL KRDS 클래스 전환 + egov-* 제거 | 2026-07-01 |
| Board 4개 FTL KRDS 클래스 전환 + egov-* 제거 | 2026-07-01 |
| MasterDetail 3개 FTL KRDS 클래스 전환 + egov-* 제거 | 2026-07-01 |
| 레이아웃 2개 krds-main-menu / data-layout-sidebar / inline footer 전환 | 2026-07-01 |
| 레이아웃 2개 styles.css 링크 적용 | 2026-07-01 |
| FilePlanFactory, ProjectValidator, Tool 설명 styles.css 기준 변경 | 2026-07-01 |
