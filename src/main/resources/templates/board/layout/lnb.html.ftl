<aside th:fragment="lnb" class="egov-lnb">
    <nav aria-label="좌측 메뉴">
        <strong th:text="${r"${lnbTitle ?: '소식·뉴스'}"}" class="egov-lnb-title">소식·뉴스</strong>
        <ul class="egov-lnb-list">
            <li th:each="menu : ${r"${lnbMenus}"}">
                <a th:href="${r"${menu.url}"}"
                   class="lnb-link"
                   th:text="${r"${menu.label}"}"
                   th:classappend="${r"${menu.menuId == currentMenuId} ? 'lnb-active'"}">목록</a>
            </li>
            <li th:if="${r"${#lists.isEmpty(lnbMenus)}"}" class="egov-lnb-empty">하위 메뉴가 없습니다.</li>
        </ul>
    </nav>
</aside>
