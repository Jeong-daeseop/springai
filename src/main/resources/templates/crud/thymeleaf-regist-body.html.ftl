<#--
  등록 화면 body.
  designComponentPlan이 있으면(V2_APPLY 픽셀 재현) KRDS 컴포넌트 fragment(th:replace) 기반
  div-stack 폼으로 렌더하고, 없으면 기존 table 폼을 그대로 유지한다(산출물 바이트 동일).
-->
<#macro registFormField f>
  <#if designComponentPlan.isCommonCode(f.javaName) && designComponentPlan.has('select')>
                <div th:replace="~{components/krds-select :: select(path='${f.javaName}', label='${f.comment}', size='medium', state=null, options=${'$'}{${f.javaName}CodeList}, required=${f.required?c})}"></div>
  <#elseif (f.javaType == 'LocalDate' || f.javaType == 'LocalDateTime' || f.javaType == 'Date') && designComponentPlan.has('date-input')>
                <div th:replace="~{components/krds-date-input :: dateInput(path='${f.javaName}', label='${f.comment}', mode='single', required=${f.required?c})}"></div>
  <#elseif designComponentPlan.has('text-input')>
                <div th:replace="~{components/krds-text-input :: textInput(path='${f.javaName}', label='${f.comment}', size='medium', state=null, placeholder='${f.comment}을(를) 입력하세요', maxlength=<#if f.maxLength??>${f.maxLength?c}<#else>null</#if>, required=${f.required?c})}"></div>
  <#else>
                <div class="krds-form-group">
                    <label for="${f.javaName}" class="krds-form-label">
                        ${f.comment}<#if f.required><span class="egov-required-mark">*</span></#if>
                    </label>
                    <input type="<#if f.javaName?lower_case?contains('password')>password<#else>text</#if>"
                           th:field="*{${f.javaName}}"
                           id="${f.javaName}"
                           class="krds-input medium egov-control"
                           <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                           placeholder="${f.comment}을(를) 입력하세요"/>
                    <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                       class="krds-form-hint is-error"
                       th:errors="*{${f.javaName}}"></p>
                </div>
  </#if>
</#macro>
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
<#if designComponentPlan??>
        <div class="egov-form-body<#if formColumnLayout == "TWO_COLUMN"> egov-layout-two-col</#if>">
<#list pkFields as f>
            <div class="krds-form-group">
                <label for="${f.javaName}" class="krds-form-label">
                    ${f.comment}<span class="egov-required-mark">*</span>
                </label>
                <input type="text"
                       th:field="*{${f.javaName}}"
                       id="${f.javaName}"
                       class="krds-input medium egov-control"
                       <#if f.maxLength??>maxlength="${f.maxLength?c}"</#if>
                       placeholder="${f.comment}을(를) 입력하세요"/>
                <p th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                   class="krds-form-hint is-error"
                   th:errors="*{${f.javaName}}"></p>
            </div>
</#list>
<#if formColumnLayout == "TWO_COLUMN">
<#list formFields?chunk(2) as pair>
            <div class="form-row-two-col">
<#list pair as f><@registFormField f/></#list>
            </div>
</#list>
<#else>
<#list formFields as f><@registFormField f/></#list>
</#if>
        </div>
<#else>
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
<#if pair?size == 1>
                    <th></th>
                    <td></td>
</#if>
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
</#if>

        <div class="egov-form-actions">
<#if designComponentPlan?? && designComponentPlan.has('button')>
            <a th:href="@{${route.resolvedListPath()}}" th:replace="~{components/krds-button :: button(label='취소', variant='secondary', size='medium', buttonType='button')}"></a>
            <button type="submit" th:replace="~{components/krds-button :: button(label='저장', variant='primary', size='medium', buttonType='submit')}"></button>
<#else>
            <a th:href="@{${route.resolvedListPath()}}" class="krds-btn secondary medium egov-btn">취소</a>
            <button type="submit" class="krds-btn primary medium egov-btn">
                <span aria-hidden="true">✓</span>
                저장
            </button>
</#if>
        </div>
    </form>
