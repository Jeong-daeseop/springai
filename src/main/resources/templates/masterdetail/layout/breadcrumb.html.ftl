<nav th:fragment="breadcrumb" class="krds-breadcrumb-wrap" aria-label="breadcrumb">
    <th:block th:each="crumb, stat : ${r"${breadcrumbs}"}">
        <a th:if="${r"${crumb.url != null}"}" th:href="${r"${crumb.url}"}" th:text="${r"${crumb.label}"}">홈</a>
        <span th:unless="${r"${crumb.url != null}"}" aria-current="page" th:text="${r"${crumb.label}"}">현재</span>
        <span th:if="${r"${!stat.last}"}">›</span>
    </th:block>
</nav>
