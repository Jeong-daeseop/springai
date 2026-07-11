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
            <span aria-hidden="true" class="egov-brand-mark header">e</span>
            <span>eGovFrame</span>
        </a>
        <nav class="egov-main-menu" aria-label="주 메뉴">
            <ul class="egov-main-menu-list">
                <li class="egov-mega-item">
                    <a th:href="@{/}" class="egov-main-menu-link gnb-main-trigger is-link"
                       th:classappend="${r"${currentMenuId == 'home'} ? 'gnb-active'"}">홈</a>
                </li>
                <li class="egov-mega-item" th:each="menu : ${r"${gnbMenus}"}">
                    <a th:href="@{${r"${!#lists.isEmpty(menu.children) ? (!#lists.isEmpty(menu.children[0].children) ? menu.children[0].children[0].url : menu.children[0].url) : menu.url}"}}" class="egov-main-menu-link gnb-main-trigger is-link"
                       th:if="${r"${menu.url != null}"}"
                       th:text="${r"${menu.menuNm}"}"
                       th:classappend="${r"${menu.menuNo == currentTopMenuNo} ? 'gnb-active'"}"></a>
                    <a href="#" class="egov-main-menu-link gnb-main-trigger is-link"
                       th:if="${r"${menu.url == null}"}"
                       th:text="${r"${menu.menuNm}"}"
                       th:classappend="${r"${menu.menuNo == currentTopMenuNo} ? 'gnb-active'"}"></a>
                    <div class="egov-mega-panel" th:if="${r"${!#lists.isEmpty(menu.children)}"}">
                        <div class="egov-dropdown-inner">
                            <ul class="egov-dropdown-side">
                                <li th:each="child, cStat : ${r"${menu.children}"}"
                                    th:attr="data-dd-idx=${r"${cStat.index}"}">
                                    <a th:href="@{${r"${child.url}"}}" class="egov-dropdown-side-link"
                                       th:text="${r"${child.menuNm}"}"></a>
                                </li>
                            </ul>
                            <div class="egov-dropdown-content">
                                <div class="egov-dropdown-group" th:each="child, cStat : ${r"${menu.children}"}"
                                     th:attr="data-dd-idx=${r"${cStat.index}"}">
                                    <p class="egov-dropdown-title" th:text="${r"${child.menuNm}"}">메뉴</p>
                                    <ul class="egov-dropdown-list">
                                        <li th:each="grandchild : ${r"${child.children}"}" th:if="${r"${grandchild.url != null}"}">
                                            <a th:href="@{${r"${grandchild.url}"}}" class="egov-dropdown-link"
                                               th:text="${r"${grandchild.menuNm}"}"></a>
                                        </li>
                                        <li th:if="${r"${#lists.isEmpty(child.children) and child.url != null}"}">
                                            <a th:href="@{${r"${child.url}"}}" class="egov-dropdown-link"
                                               th:text="${r"${child.menuNm}"}"></a>
                                        </li>
                                    </ul>
                                </div>
                            </div>
                        </div>
                    </div>
                </li>
            </ul>
        </nav>
    </div>
</header>
<script>
document.querySelectorAll('.egov-mega-item').forEach(function (item) {
    var sideLinks = item.querySelectorAll('.egov-dropdown-side > li');
    var groups = item.querySelectorAll('.egov-dropdown-group');
    if (!sideLinks.length || !groups.length) { return; }

    function activate(idx) {
        sideLinks.forEach(function (li) {
            var a = li.querySelector('a');
            if (a) { a.classList.remove('is-active'); }
        });
        groups.forEach(function (g) { g.classList.remove('is-active'); });
        var activeLink = sideLinks[idx] && sideLinks[idx].querySelector('a');
        if (activeLink) { activeLink.classList.add('is-active'); }
        if (groups[idx]) { groups[idx].classList.add('is-active'); }
    }

    sideLinks.forEach(function (li, idx) {
        li.addEventListener('mouseenter', function () { activate(idx); });
    });

    activate(0);
});
</script>
</th:block>
