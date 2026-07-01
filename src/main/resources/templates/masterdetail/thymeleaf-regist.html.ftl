<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${master.domainKr} 등록</title>
</head>
<th:block layout:fragment="content">
    <th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #1e2124;">
        <h1>${master.domainKr} 등록</h1>
    </div>

    <div style="display:flex;align-items:center;gap:4px;justify-content:flex-end;margin-bottom:12px;font-size:13px;color:#6d7882;">
        <span style="margin-left:2px;color:#d9363e;font-weight:700;" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form th:action="@{${urlPrefix}Regist.do}" th:object="${'$'}{${master.domainLc}VO}" method="post">
        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${master.domainKr} 등록 입력 폼</caption>
                <tbody>
<#list master.fields as f>
<#if !f.pk>
                <tr>
                    <th scope="row">
                        <label for="${f.javaName}">
                            ${f.comment}<#if f.required><span style="margin-left:2px;color:#d9363e;font-weight:700;">*</span></#if>
                        </label>
                    </th>
                    <td>
                        <input type="text"
                               th:field="*{${f.javaName}}"
                               id="${f.javaName}"
                               class="krds-input"
                               <#if f.maxLength??>maxlength="${f.maxLength}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p style="display:flex;align-items:center;gap:4px;margin:5px 0 0;font-size:12px;color:#d9363e;"
                           th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
                </tr>
</#if>
</#list>
                </tbody>
            </table>
        </div>

        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:28px;">
            <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
</th:block>
</html>
