    <div class="egov-page-header">
        <h1 class="egov-page-title">${domainKr} 등록</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form class="egov-search-form" th:object="${'$'}{${domainLc}VO}" th:action="@{${route.resolvedRegistPath()}}" method="post">
        <input th:if="${'$'}{_csrf != null}" type="hidden"
               th:name="${'$'}{_csrf.parameterName}" th:value="${'$'}{_csrf.token}"/>
        <div class="krds-table-wrap">
            <table class="tbl col egov-form-table<#if formColumnLayout == "TWO_COLUMN"> egov-layout-two-col</#if>">
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
                               class="krds-input medium egov-control"
                               <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           class="egov-field-error"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
                </tr>
</#list>
<#if formColumnLayout == "TWO_COLUMN">
<#list formFields?chunk(2) as pair>
                <tr>
<#list pair as f>
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
</#list>
                </tr>
</#list>
<#else>
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
                               class="krds-input medium egov-control"
                               <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                               placeholder="${f.comment}을(를) 입력하세요"/>
                        <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                           class="egov-field-error"
                           th:errors="*{${f.javaName}}"></p>
                    </td>
                </tr>
</#list>
</#if>
                </tbody>
            </table>
        </div>

        <div class="egov-form-actions">
            <a th:href="@{${route.resolvedListPath()}}" class="krds-btn secondary medium egov-btn">취소</a>
            <button type="submit" class="krds-btn primary medium egov-btn">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
