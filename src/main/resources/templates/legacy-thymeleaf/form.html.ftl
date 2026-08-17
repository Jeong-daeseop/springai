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
<#if designTokenCssVariableNames?has_content>
    <!-- egov-design-token-provenance: <#list designTokenCssVariableNames as v>${v}<#sep>, </#sep></#list> -->
</#if>
    <div class="egov-page-header">
        <h1 class="egov-page-title">${pageTitle}</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form class="egov-legacy-form"
          th:object="${'$'}{${route.modelAttributeName()}}"
          th:action="@{${route.route()}}"
          method="<#if route.httpMethod()=='GET'>get<#else>post</#if>">
        <input th:if="${'$'}{_csrf != null}" type="hidden"
               th:name="${'$'}{_csrf.parameterName}" th:value="${'$'}{_csrf.token}"/>
        <div class="krds-table-wrap"
             data-egov-responsive="single-column-below-tablet"
             data-egov-breakpoint-tablet="768">
            <table class="tbl col egov-form-table<#if formColumnLayout == "TWO_COLUMN"> egov-layout-two-col</#if>">
                <caption>${pageTitle} 입력 폼</caption>
                <tbody>
<#list formFields as f>
                <tr>
                    <th scope="row">
                        <label for="${f.fieldName()}">${f.fieldName()}<#if f.required()><span class="egov-required-mark">*</span></#if></label>
                    </th>
                    <td>
                        <input type="text"
                               th:field="*{${f.fieldName()}}"
                               id="${f.fieldName()}"
                               class="krds-input medium egov-control"/>
                        <p th:if="${'$'}{#fields.hasErrors('${f.fieldName()}')}"
                           class="egov-field-error"
                           th:errors="*{${f.fieldName()}}"></p>
                    </td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>

        <div class="egov-form-actions">
            <button type="submit" class="krds-btn primary medium egov-btn">저장</button>
        </div>
    </form>
</section>
</html>
