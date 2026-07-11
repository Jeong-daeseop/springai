    <div class="egov-page-header">
        <h1 class="egov-page-title">${domainKr} 등록</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form th:object="${'$'}{${domainLc}VO}" th:action="@{${urlPrefix}Regist.do}" method="post">
        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${domainKr} 등록 입력 폼</caption>
                <tbody>
<#list pkFields as f>
                <tr>
                    <th scope="row">
                        <label for="${f.javaName}">
                            ${f.comment}<span class="egov-required-mark">*</span>
                        </label>
                    </th>
                    <td>
                        <input type="text"
                               th:field="*{${f.javaName}}"
                               id="${f.javaName}"
                               class="krds-input"
                               <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           class="egov-field-error"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
                </tr>
</#list>
<#list formFields as f>
                <tr>
                    <th scope="row">
                        <label for="${f.javaName}">
                            ${f.comment}<#if f.required><span class="egov-required-mark">*</span></#if>
                        </label>
                    </th>
                    <td>
                        <input type="<#if f.javaName?lower_case?contains('password')>password<#else>text</#if>"
                               th:field="*{${f.javaName}}"
                               id="${f.javaName}"
                               class="krds-input"
                               <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           class="egov-field-error"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>

        <div class="egov-form-actions">
            <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
