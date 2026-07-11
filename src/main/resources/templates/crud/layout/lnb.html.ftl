<aside th:fragment="lnb" class="egov-lnb">
    <nav aria-label="좌측 메뉴">
        <strong th:text="${r"${lnbTitle ?: '업무관리'}"}" class="egov-lnb-title">업무관리</strong>
        <ul class="egov-lnb-list">
            <li th:each="menu : ${r"${lnbMenus}"}">
                <a th:if="${r"${menu.url != null}"}" th:href="${r"${menu.url}"}" class="lnb-link"
                   th:classappend="${r"${(menu.menuId == currentMenuId ? ' lnb-active' : '') + (!#lists.isEmpty(menu.children) ? ' has-children' : '')}"}">
                    <span th:text="${r"${menu.label}"}">목록</span>
                    <span th:if="${r"${!#lists.isEmpty(menu.children)}"}" class="lnb-chevron" aria-hidden="true">&rsaquo;</span>
                </a>
                <span th:unless="${r"${menu.url != null}"}" class="lnb-link lnb-group-label"
                      th:classappend="${r"${!#lists.isEmpty(menu.children)} ? ' has-children'"}">
                    <span th:text="${r"${menu.label}"}">목록</span>
                    <span th:if="${r"${!#lists.isEmpty(menu.children)}"}" class="lnb-chevron" aria-hidden="true">&rsaquo;</span>
                </span>
                <ul th:if="${r"${!#lists.isEmpty(menu.children)}"}" class="egov-lnb-sublist">
                    <li th:each="child : ${r"${menu.children}"}">
                        <a th:if="${r"${child.url != null}"}" th:href="${r"${child.url}"}" class="lnb-link lnb-sublink"
                           th:classappend="${r"${child.menuId == currentMenuId} ? 'lnb-active'"}">
                            <span th:text="${r"${child.label}"}">하위메뉴</span>
                        </a>
                    </li>
                </ul>
            </li>
            <li th:if="${r"${#lists.isEmpty(lnbMenus)}"}" class="egov-lnb-empty">하위 메뉴가 없습니다.</li>
        </ul>
    </nav>
</aside>
