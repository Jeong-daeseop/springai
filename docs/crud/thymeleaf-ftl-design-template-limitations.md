# 현재 Thymeleaf FTL의 Design Templates 100% 구현 불가 항목

> 작성일: 2026-07-01
>
> 기준 문서: `docs/crud/thymeleaf-ftl-design-template-gap-analysis.md`
>
> 참조 원본: `/Users/jeongdaeseob/Downloads/KRDS Design System/templates/`

## 결론

현재 `CrudPromptBuilderTool` 계열 Thymeleaf FTL은 KRDS 클래스 기반 화면으로 전환되어 있지만,
Downloads의 Design Templates를 픽셀, 구조, 상호작용까지 100% 그대로 재현하는 수준은 아니다.

주요 원인은 다음과 같다.

| 구분 | 한계 |
|---|---|
| 스타일 방식 | Design Templates는 인라인 스타일 중심, FTL은 `krds-*` / `egov-*` 클래스 중심 |
| 레이아웃 | CRUD/Board는 generic GNB/LNB/Footer, MasterDetail은 GNB/LNB/Footer 없음 |
| 상호작용 | 모달, 토스트, 체크박스 일괄 선택/삭제 등 Design Template UI 미구현 |
| 데이터 구조 | FTL은 `fields`, `listFields`, `detailFields` 루프 기반이라 고정 컬럼 디자인과 불일치 |
| 게시판 특화 UI | 공지 고정 행, 첨부 아이콘, 이전글/다음글, 파일 업로드 UI 미구현 |

---

## 공통 한계

### 1. 스타일링 방식 불일치

Design Templates는 대부분 `style="..."` 인라인 스타일로 레이아웃, 간격, 색상, 버튼 크기, 테이블 모양을 직접 지정한다.

현재 FTL은 다음 클래스 체계에 의존한다.

- `krds-btn`
- `krds-table-wrap`
- `tbl col`
- `krds-input`
- `krds-form-select`
- `krds-pagination`
- `egov-breadcrumb`
- `egov-page-title`
- `egov-alert`
- `egov-btn-area`

따라서 같은 UI 요소를 표현하더라도 버튼 정렬, 테이블 헤더 색상, 입력 필드 높이, 페이지 간격이 Design Templates와 1:1로 일치하지 않을 수 있다.

### 2. GNB/LNB/Footer 구조 불일치

| 영역 | Design Templates | 현재 FTL |
|---|---|---|
| GNB | `GnbNav.dc.html` 컴포넌트 | CRUD/Board는 generic `egov-gnb`, MasterDetail은 없음 |
| LNB | 업무별 메뉴 (`소식·뉴스`, `업무관리` 등) | CRUD/Board는 generic `업무관리`, MasterDetail은 없음 |
| Footer | FTC 스타일 full footer | CRUD/Board는 generic `egov-footer`, MasterDetail은 없음 |

MasterDetail은 `masterdetail/layout/default.html.ftl`이 단순 `<div class="container"><main>` 구조라 GNB/LNB/Footer가 제공되지 않는다.

### 3. MasterDetail egov-* CSS 미공급

MasterDetail 화면은 다음 `egov-*` 구조 클래스를 사용한다.

- `egov-breadcrumb`
- `egov-page-title`
- `egov-alert`
- `egov-btn-area`
- `egov-search-box`
- `egov-search-row`

하지만 이 CSS 정의는 `crud/layout/default.html.ftl` 인라인 `<style>`에만 있고, `masterdetail/layout/default.html.ftl`에는 없다.

결과적으로 MasterDetail 3개 화면은 마크업은 존재하지만 구조 스타일이 공급되지 않는 렌더링 버그가 있다.

### 4. 공통 상호작용 미구현

| Design Templates | 현재 FTL |
|---|---|
| 삭제 확인 모달 | 브라우저 `confirm()` |
| 완료 토스트 | redirect + flash message |
| SVG 포함 Empty State | 텍스트 메시지 중심 |
| URL 복사 / 프린트 버튼 | 대부분 없음 |
| 페이지당 건수 select | 없음 |

---

## CRUD 일반형 한계

대상:

- `crud/thymeleaf-list.html.ftl`
- `crud/thymeleaf-detail.html.ftl`
- `crud/thymeleaf-regist.html.ftl`
- `crud/thymeleaf-updt.html.ftl`

참조 Design Templates:

- `ftc-list/FtcList.dc.html`
- `ftc-detail/FtcDetail.dc.html`

### 목록 화면

`crud/thymeleaf-list.html.ftl`은 `FtcList.dc.html` 대비 다음 요소가 부족하다.

| 항목 | 상태 |
|---|---|
| 구분 탭 | 미구현 |
| 기간 검색 | 미구현 |
| 페이지당 건수 select | 미구현 |
| 구분/카테고리 배지 | 미구현 |
| 담당부서/등록일/첨부아이콘 고정 컬럼 | `listFields` 루프 의존 |
| SVG Empty State | 텍스트만 |
| URL 복사 / 프린트 | 미구현 |

### 상세 화면

`crud/thymeleaf-detail.html.ftl`은 `FtcDetail.dc.html` 대비 다음 요소가 부족하다.

| 항목 | 상태 |
|---|---|
| 게시물 제목 + 메타바 | `fields` 루프 기반으로 구조 다름 |
| 첨부파일 다운로드 링크 | 실제 파일명/크기 링크 없음 |
| 풍부한 HTML 본문 영역 | `fields` 루프 포함 여부에 의존 |
| 이전글/다음글 네비게이션 | 미구현 |
| URL 복사 / 프린트 | 미구현 |

### 등록/수정 화면

`crud/thymeleaf-regist.html.ftl`, `crud/thymeleaf-updt.html.ftl`에 직접 대응하는 Design Template은 없다.

`FtcBoardForm.dc.html`은 Board 전용이고, `FtcApply.dc.html`은 민원·신청 양식 특화 템플릿이다.

---

## Board 한계

대상:

- `board/thymeleaf-list.html.ftl`
- `board/thymeleaf-detail.html.ftl`
- `board/thymeleaf-regist.html.ftl`
- `board/thymeleaf-updt.html.ftl`

참조 Design Templates:

- `ftc-board/FtcBoard.dc.html`
- `ftc-board-detail/FtcBoardDetail.dc.html`
- `ftc-board-form/FtcBoardForm.dc.html`

### 목록 화면

| 항목 | 상태 |
|---|---|
| 공지 고정 행 | 미구현 |
| 첨부 아이콘 컬럼 | 미구현 |
| 조회수/등록일 고정 컬럼 | `listFields` 루프 의존 |
| 페이지당 건수 select | 미구현 |
| URL 복사 / 프린트 | 미구현 |

### 상세 화면

| 항목 | 상태 |
|---|---|
| 게시글 4컬럼 정의 테이블 | `fields` 루프 기반으로 구조 다름 |
| 첨부파일명·용량 다운로드 링크 | `atchFileId` 수준 |
| 이전글/다음글 네비게이션 | 미구현 |
| 본문 전용 영역 | `fields` 루프 포함 여부에 의존 |
| 하단 버튼 좌우 배치 | `egov-btn-area` 구조와 다름 |

### 등록/수정 화면

`board/thymeleaf-regist.html.ftl`, `board/thymeleaf-updt.html.ftl`은 `FtcBoardForm.dc.html` 대비 다음 요소가 부족하다.

| 항목 | 상태 |
|---|---|
| 카테고리 select | 미구현 |
| 공개여부 radio | 미구현 |
| 제목 전용 input (`maxlength=200`) | generic input |
| 작성자·담당부서 2컬럼 행 | 단독 행 |
| 내용 textarea | `<input type="text">`만 사용 |
| 글자수 카운터 | 미구현 |
| 드래그 파일 업로드존 | 미구현 |
| 첨부파일 목록/삭제 UI | 미구현 |
| 전체 유효성 오류 배너 | 개별 필드 오류만 |
| 저장 성공 토스트 | redirect 방식 |

---

## MasterDetail 한계

대상:

- `masterdetail/thymeleaf-list.html.ftl`
- `masterdetail/thymeleaf-detail.html.ftl`
- `masterdetail/thymeleaf-regist.html.ftl`

참조 Design Templates:

- `ftc-crud-master/FtcCrudMaster.dc.html`
- `ftc-crud-detail/FtcCrudDetail.dc.html`

### 목록 화면

`masterdetail/thymeleaf-list.html.ftl`은 `FtcCrudMaster.dc.html` 대비 다음 요소가 부족하다.

| 항목 | 상태 |
|---|---|
| GNB/LNB/Footer | masterdetail layout에 없음 |
| 체크박스 컬럼 + 전체 선택 | 미구현 |
| 선택 삭제 / 일괄 삭제 | 미구현 |
| 상태 배지 컬럼 | 미구현 |
| 페이지당 건수 select | 미구현 |
| 삭제 확인 모달 | `confirm()` 사용 |
| 삭제 완료 토스트 | redirect 방식 |
| SVG Empty State | 텍스트만 |
| egov-* CSS 공급 | 미공급 |

행별 수정·삭제 버튼은 존재하지만, Design Template의 아이콘 버튼, 모달, 선택 상태 UI와는 구조가 다르다.

### 상세 화면

`masterdetail/thymeleaf-detail.html.ftl`은 `FtcCrudDetail.dc.html` 대비 다음 요소가 부족하다.

| 항목 | 상태 |
|---|---|
| GNB/LNB/Footer | masterdetail layout에 없음 |
| 마스터 정보 고정 4컬럼 구조 | `fields` 루프 기반 |
| 마스터 상태 배지 | 미구현 |
| 디테일 목록 고정 7컬럼 구조 | `detailFields` 루프 기반 |
| 디테일 행 상태 배지 | 미구현 |
| 디테일 총건수 표시 | 미구현 |
| 마스터/디테일 삭제 모달 | `confirm()` 사용 |
| 토스트 알림 | redirect 방식 |
| 하단 버튼 좌우 배치 | `egov-btn-area` 구조와 다름 |
| egov-* CSS 공급 | 미공급 |

### 등록 화면

`masterdetail/thymeleaf-regist.html.ftl`에 직접 대응하는 Design Template은 없다.

현재 화면은 generic form table 구조이며, Design Templates의 고정 업무 화면 구조와 1:1 비교 기준이 없다.

---

## 우선 보완 대상

100% 구현에 가까워지려면 다음 순서로 보완하는 것이 현실적이다.

1. MasterDetail layout을 `crud/layout` 수준으로 맞추거나 공통 layout으로 통합한다.
2. `egov-*` 구조 CSS를 인라인 중복이 아닌 공통 CSS 파일로 분리한다.
3. Board 목록/상세/폼의 게시판 전용 요소를 추가한다.
4. MasterDetail 목록의 체크박스, 일괄 삭제, 상태 배지, 모달, 토스트를 추가한다.
5. CRUD 일반형 목록/상세를 `FtcList`, `FtcDetail` 구조에 맞춰 재설계한다.
6. 인라인 스타일 기반 Design Template과 클래스 기반 FTL 사이의 토큰/간격 차이를 시각 검증으로 조정한다.

