<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${master.domainKr} 등록</title>
</head>
<th:block layout:fragment="content">
<h4 class="mb-3">${master.domainKr} 등록</h4>

<form th:action="@{${urlPrefix}Regist.do}" th:object="${'$'}{${master.domainLc}VO}" method="post">
    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>${master.domainKr} 등록</caption>
            <tbody>
<#list master.fields as f>
            <tr>
                <th scope="row">${f.comment}</th>
                <td>
                    <input type="text" th:field="*{${f.javaName}}" <#if f.pk>required</#if>>
                    <div class="text-danger" th:if="${'$'}{#fields.hasErrors('${f.javaName}')}" th:errors="*{${f.javaName}}"></div>
                </td>
            </tr>
</#list>
            </tbody>
        </table>
    </div>
    <div class="btn-area mt-3">
        <button type="submit" class="krds-btn primary medium">저장</button>
        <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">취소</a>
    </div>
</form>
</th:block>
</html>
