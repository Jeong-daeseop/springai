<th:block th:fragment="gnb">
<div class="egov-header-top">
    <div class="egov-header-top-inner">
        <span>이 누리집은 대한민국 공식 전자정부 프레임워크 예제 화면입니다.</span>
        <span>통합검색 · 로그인 · 화면크기</span>
    </div>
</div>
<header class="egov-header">
    <div class="egov-header-inner">
        <a th:href="@{/}" class="egov-header-brand">
            <img th:src="@{/resources/images/egov-logo.png}" alt="eGovFrame" class="egov-brand-logo">
        </a>
        <nav class="egov-main-menu" aria-label="주 메뉴">
            <ul class="egov-main-menu-list">
                <li class="egov-mega-item">
                    <a th:href="@{/}" class="egov-main-menu-link gnb-main-trigger is-link"
                       th:classappend="${r"${currentMenuId == 'home'} ? 'gnb-active'"}">홈</a>
                </li>
                <li class="egov-mega-item" th:each="menu : ${r"${gnbMenus}"}">
                    <a th:href="@{${r"${menu.url}"}}" class="egov-main-menu-link gnb-main-trigger is-link"
                       th:if="${r"${menu.url != null}"}"
                       th:text="${r"${menu.menuNm}"}"
                       th:classappend="${r"${menu.menuNo == currentTopMenuNo} ? 'gnb-active'"}"></a>
                    <a href="#" class="egov-main-menu-link gnb-main-trigger is-link"
                       th:if="${r"${menu.url == null}"}"
                       th:text="${r"${menu.menuNm}"}"
                       th:classappend="${r"${menu.menuNo == currentTopMenuNo} ? 'gnb-active'"}"></a>
                    <div class="egov-mega-panel" th:if="${r"${!#lists.isEmpty(menu.children)}"}">
                        <p class="egov-mega-title" th:text="${r"${menu.menuNm}"}">메뉴</p>
                        <ul class="egov-mega-list">
                            <li th:each="child : ${r"${menu.children}"}" th:if="${r"${child.url != null}"}">
                                <a th:href="@{${r"${child.url}"}}" class="egov-mega-link"
                                   th:text="${r"${child.menuNm}"}"></a>
                            </li>
                        </ul>
                    </div>
                </li>
            </ul>
        </nav>
    </div>
</header>
</th:block>
