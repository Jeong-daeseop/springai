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
    </div>

    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>${pageTitle} 정보</caption>
            <tbody>
<#if primaryDisplayAttributeName??>
<#list displayFields as f>
                <tr>
                    <th scope="row">${f.fieldName()}</th>
                    <td th:text="${'$'}{${primaryDisplayAttributeName}.${f.fieldName()}}"></td>
                </tr>
</#list>
</#if>
            </tbody>
        </table>
    </div>

<#if secondaryDisplayAttributeName??>
    <div class="krds-table-wrap">
        <table class="tbl data">
            <caption>${pageTitle} 관련 목록</caption>
            <thead>
            <tr>
<#list secondaryDisplayFields as f>
                <th scope="col">${f}</th>
</#list>
            </tr>
            </thead>
            <tbody>
            <tr th:each="item : ${'$'}{${secondaryDisplayAttributeName}}">
<#list secondaryDisplayFields as f>
                <td th:text="${'$'}{item.${f}}"></td>
</#list>
            </tr>
            <tr th:if="${'$'}{#lists.isEmpty(${secondaryDisplayAttributeName})}">
                <td colspan="${secondaryDisplayFields?size}" class="egov-empty-cell">관련 항목이 없습니다.</td>
            </tr>
            </tbody>
        </table>
    </div>
</#if>

    <div class="egov-form-actions">
        <a th:href="@{${route.route()}}" class="krds-btn secondary medium egov-btn">목록</a>
    </div>
</section>
</html>
