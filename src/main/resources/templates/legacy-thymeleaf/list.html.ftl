<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{${layoutView}}">
<head>
    <title>${pageTitle}</title>
</head>
<section layout:fragment="content" class="egov-legacy-page">
<#if route.securityEvidence()?has_content>
    <!-- egov-authority-provenance: <#list route.securityEvidence() as s>${s}<#sep>; </#sep></#list> -->
</#if>
    <div class="egov-page-header">
        <h1 class="egov-page-title">${pageTitle}</h1>
<#if registRoute?? && actionPlacement == "TOP_RIGHT">
        <a th:href="@{${registRoute}}" class="krds-btn primary medium egov-btn egov-btn-register">등록</a>
</#if>
    </div>

    <div id="toast-alert" th:if="${'$'}{message}" class="egov-toast" role="alert" aria-live="polite">
        <span th:text="${'$'}{message}">처리되었습니다.</span>
    </div>

    <div class="egov-search-panel">
        <form class="egov-search-form" th:action="@{${route.route()}}"
              method="<#if route.httpMethod()=='POST'>post<#else>get</#if>">
            <input type="text" name="searchKeyword" class="krds-input medium egov-control"
                   th:value="${'$'}{param.searchKeyword}" placeholder="검색어를 입력하세요"/>
            <button type="submit" class="krds-btn primary medium egov-btn">검색</button>
        </form>
    </div>

    <div class="krds-table-wrap egov-density-${layoutDensity?lower_case}"
         data-egov-responsive="table-to-card"
         data-egov-breakpoint-tablet="768"
         data-egov-breakpoint-mobile="390">
        <table class="tbl data egov-list-table">
            <caption>${pageTitle} 표</caption>
            <thead>
            <tr>
<#list displayFields as f>
                <th scope="col">${f.fieldName()}</th>
</#list>
                <th scope="col">관리</th>
            </tr>
            </thead>
            <tbody>
<#if primaryDisplayAttributeName??>
            <tr th:each="item : ${'$'}{${primaryDisplayAttributeName}}">
<#list displayFields as f>
                <td th:text="${'$'}{item.${f.fieldName()}}"></td>
</#list>
                <td class="egov-table-actions"></td>
            </tr>
            <tr th:if="${'$'}{#lists.isEmpty(${primaryDisplayAttributeName})}">
                <td colspan="${(displayFields?size + 1)?c}" class="egov-empty-cell">조회된 데이터가 없습니다.</td>
            </tr>
<#else>
            <tr>
                <td colspan="${(displayFields?size + 1)?c}" class="egov-empty-cell">표시할 데이터가 없습니다.</td>
            </tr>
</#if>
            </tbody>
        </table>
    </div>

<#if registRoute?? && actionPlacement == "BOTTOM_RIGHT">
    <div class="egov-form-actions">
        <a th:href="@{${registRoute}}" class="krds-btn primary medium egov-btn egov-btn-register">등록</a>
    </div>
</#if>
</section>
</html>
