    <div class="egov-page-header">
        <h1 class="egov-page-title">${master.domainKr} 수정</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form class="egov-search-form" th:object="${'$'}{${master.domainLc}VO}" th:action="@{${urlPrefix}Updt.do}" method="post">
        <input th:if="${'$'}{_csrf != null}" type="hidden"
               th:name="${'$'}{_csrf.parameterName}" th:value="${'$'}{_csrf.token}"/>
<#list master.pkFields as p>
        <input type="hidden" th:field="*{${p.javaName}}"/>
</#list>
<#list master.nonPkFields as f>
<#if !master.formFields?seq_contains(f)>
        <input type="hidden" th:field="*{${f.javaName}}"/>
</#if>
</#list>

        <div class="krds-table-wrap">
            <table class="tbl col egov-form-table">
                <caption>${master.domainKr} 수정 입력 폼</caption>
                <tbody>
<#list master.pkFields as p>
                <tr>
                    <th scope="row">${p.comment}</th>
                    <td>
                        <span th:text="${'$'}{${master.domainLc}VO.${p.javaName}}" class="egov-readonly-value"></span>
                    </td>
                </tr>
</#list>
<#list master.formFields as f>
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
                               class="krds-input medium egov-control"
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
            <a th:href="@{${urlPrefix}Detail.do(<#list master.pkFields as p>${p.javaName}=${'$'}{${master.domainLc}VO.${p.javaName}}<#sep>,</#sep></#list>)}"
               class="krds-btn secondary medium egov-btn">취소</a>
            <button type="submit" class="krds-btn primary medium egov-btn">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
