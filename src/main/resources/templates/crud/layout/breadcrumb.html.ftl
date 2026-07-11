<div th:fragment="breadcrumb"
     class="egov-breadcrumb-band"
     th:if="${r"${breadcrumbs != null and !#lists.isEmpty(breadcrumbs)}"}">
    <nav class="egov-breadcrumb" aria-label="현재 위치">
        <th:block th:each="crumb, stat : ${r"${breadcrumbs}"}">
            <a th:if="${r"${crumb.url != null and !stat.last}"}"
               th:href="${r"${crumb.url}"}"
               th:classappend="${r"${stat.first} ? 'egov-breadcrumb-home' : ''"}"
               th:aria-label="${r"${stat.first} ? '홈' : null"}">
                <span th:if="${r"${stat.first}"}" aria-hidden="true">⌂</span>
                <span th:unless="${r"${stat.first}"}" th:text="${r"${crumb.label}"}">상위</span>
            </a>
            <span th:if="${r"${crumb.url == null or stat.last}"}"
                  th:class="${r"${stat.last} ? 'egov-breadcrumb-current' : ''"}"
                  th:aria-current="${r"${stat.last} ? 'page' : null"}"
                  th:text="${r"${crumb.label}"}">현재</span>
            <span th:if="${r"${!stat.last}"}" class="egov-breadcrumb-separator" aria-hidden="true">›</span>
        </th:block>
    </nav>
</div>
