<th:block th:fragment="gnb">
<div style="background:#f4f5f6;border-bottom:1px solid #e6e8ea;font-size:13px;color:#464c53;">
    <div style="max-width:1200px;margin:0 auto;padding:8px 24px;display:flex;align-items:center;justify-content:space-between;gap:16px;">
        <span>이 누리집은 대한민국 공식 전자정부 프레임워크 예제 화면입니다.</span>
        <span>통합검색 · 로그인 · 화면크기</span>
    </div>
</div>
<header style="position:relative;z-index:100;background:#fff;border-bottom:1px solid #e6e8ea;">
    <div data-layout-header-inner
         style="max-width:1200px;margin:0 auto;padding:22px 24px 18px;display:flex;align-items:center;gap:28px;">
        <a th:href="@{/}"
           style="display:flex;align-items:center;gap:10px;font-size:20px;font-weight:800;color:#083891;white-space:nowrap;">
            <span aria-hidden="true"
                  style="width:36px;height:36px;border-radius:50%;background:#083891;display:inline-flex;align-items:center;justify-content:center;color:#fff;font-size:14px;font-weight:800;">e</span>
            <span>eGovFrame</span>
        </a>
        <nav class="krds-main-menu" aria-label="주 메뉴" style="flex:1;min-width:0;">
            <ul class="gnb-menu" style="justify-content:flex-end;">
                <li>
                    <a th:href="@{/}" class="gnb-main-trigger is-link"
                       th:classappend="${r"${currentMenuId == 'home'} ? 'gnb-active'"}">홈</a>
                </li>
                <li>
                    <a th:href="${r"${lnbMenus[0].url}"}" class="gnb-main-trigger is-link"
                       th:text="${r"${lnbTitle}"}"
                       th:classappend="${r"${#strings.startsWith(currentMenuId, 'crud-')} ? 'gnb-active'"}">업무관리</a>
                </li>
                <li><a th:href="@{/}" class="gnb-main-trigger is-link">시스템관리</a></li>
                <li><a th:href="@{/}" class="gnb-main-trigger is-link">고객지원</a></li>
            </ul>
        </nav>
    </div>
</header>
</th:block>
