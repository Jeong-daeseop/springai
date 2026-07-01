<aside th:fragment="lnb" data-layout-sidebar style="width:200px;flex:none;">
    <nav aria-label="좌측 메뉴">
        <strong th:text="${r"${lnbTitle}"}"
                style="display:block;font-size:18px;font-weight:800;color:#083891;padding-bottom:14px;margin-bottom:8px;border-bottom:2px solid #083891;">업무관리</strong>
        <ul style="list-style:none;margin:0;padding:0;">
            <li th:each="menu : ${r"${lnbMenus}"}">
                <a th:href="${r"${menu.url}"}" class="lnb-link"
                   th:text="${r"${menu.label}"}"
                   th:classappend="${r"${menu.menuId == currentMenuId} ? 'lnb-active'"}">목록</a>
            </li>
        </ul>
    </nav>
</aside>
