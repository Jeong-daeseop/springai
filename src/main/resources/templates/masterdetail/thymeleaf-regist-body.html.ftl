    <div class="egov-page-header">
        <h1 class="egov-page-title">${master.domainKr} 등록</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
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
                            ${f.comment}<#if f.required><span class="egov-required-mark">*</span></#if>
                        </label>
                    </th>
                    <td>
                        <input type="text"
                               th:field="*{${f.javaName}}"
                               id="${f.javaName}"
                               class="krds-input"
                               <#if f.maxLength??>maxlength="${f.maxLength}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p class="egov-field-error"
                           th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
                </tr>
</#if>
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
