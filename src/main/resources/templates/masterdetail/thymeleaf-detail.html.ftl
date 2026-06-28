<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${master.domainKr} 상세</title>
</head>
<th:block layout:fragment="content">
<div th:if="${'$'}{message}" class="alert alert-success" role="alert">
    <span th:text="${'$'}{message}"></span>
</div>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h4 class="mb-0">${master.domainKr} 상세</h4>
    <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">목록</a>
</div>

<section class="mb-4">
    <h5>${master.domainKr} 정보</h5>
    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>${master.domainKr} 상세 정보</caption>
            <tbody>
<#list master.fields as f>
            <tr>
                <th scope="row">${f.comment}</th>
                <td th:text="${'$'}{result.${f.javaName}}"></td>
            </tr>
</#list>
            </tbody>
        </table>
    </div>
</section>

<section>
    <h5>${detail.domainKr} 목록</h5>
    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>${detail.domainKr} 목록</caption>
            <thead>
            <tr>
<#list detail.fields as f>
                <th scope="col">${f.comment}</th>
</#list>
            </tr>
            </thead>
            <tbody>
            <tr th:each="detailItem : ${'$'}{detailList}">
<#list detail.fields as f>
                <td th:text="${'$'}{detailItem.${f.javaName}}"></td>
</#list>
            </tr>
            <tr th:if="${'$'}{#lists.isEmpty(detailList)}">
                <td colspan="${detail.fields?size}">등록된 ${detail.domainKr} 정보가 없습니다.</td>
            </tr>
            </tbody>
        </table>
    </div>
</section>

<form th:action="@{${urlPrefix}Delete.do}" method="post" class="mt-3"
      onsubmit="return confirm('삭제하시겠습니까?');">
    <input type="hidden" name="${master.pk.javaName}" th:value="${'$'}{result.${master.pk.javaName}}">
    <button type="submit" class="krds-btn tertiary medium">삭제</button>
</form>
</th:block>
</html>
