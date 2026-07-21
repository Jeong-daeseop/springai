    <div class="egov-page-header">
        <h1 class="egov-page-title">${displayName} 등록</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form class="egov-search-form" th:action="@{${urlPrefix}Regist.do}" method="post">
        <input th:if="${r"${_csrf != null}"}" type="hidden"
               th:name="${r"${_csrf.parameterName}"}" th:value="${r"${_csrf.token}"}"/>
        <input type="hidden" th:name="${bbsId.javaName}"
               th:value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>

        <div class="krds-table-wrap">
            <table class="tbl col egov-form-table">
                <caption>${displayName} 등록 입력 폼</caption>
                <tbody>
<#list formFields as f>
<#assign isVisibility = f.javaName == "useAt" || f.javaName == "publicAt" || f.javaName == "secretAt">
                <tr>
                    <th scope="row">
                        <label for="${f.javaName}">
                            ${f.comment}<#if f.required><span class="egov-required-mark">*</span></#if>
                        </label>
                    </th>
                    <td>
<#if f.javaName == "nttCn">
                        <textarea class="krds-input medium egov-control egov-textarea"
                                  id="${f.javaName}"
                                  th:name="${f.javaName}"
                                  rows="12"
                                  maxlength="<#if f.maxLength??>${f.maxLength?c}<#else>2000</#if>"
                                  aria-describedby="${f.javaName}Count"
                                  oninput="document.getElementById('${f.javaName}Count').textContent=this.value.length+' / '+this.maxLength"
                                  placeholder="${f.comment}을(를) 입력하세요"
                                  th:text="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"></textarea>
                        <p id="${f.javaName}Count" class="egov-char-count" aria-live="polite">0 / <#if f.maxLength??>${f.maxLength?c}<#else>2000</#if></p>
<#elseif isVisibility>
                        <div class="egov-radio-group" role="radiogroup" aria-label="${f.comment}">
                            <label><input type="radio" th:name="${f.javaName}" value="Y"
                                          th:checked="${r"${"}${domainLc}${r"VO."}${f.javaName}${r" == 'Y'}"}/>
                                <#if f.javaName == "secretAt">비공개<#elseif f.javaName == "useAt">사용<#else>공개</#if></label>
                            <label><input type="radio" th:name="${f.javaName}" value="N"
                                          th:checked="${r"${"}${domainLc}${r"VO."}${f.javaName}${r" != 'Y'}"}/>
                                <#if f.javaName == "secretAt">공개<#elseif f.javaName == "useAt">미사용<#else>비공개</#if></label>
                        </div>
<#else>
                        <input type="text"
                               class="krds-input medium egov-control"
                               id="${f.javaName}"
                               th:name="${f.javaName}"
                               th:value="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"
                               placeholder="${f.comment}을(를) 입력하세요"/>
</#if>
                    </td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>

        <div class="egov-form-actions">
            <a th:href="@{${urlPrefix}List.do(bbsId=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"})}"
               class="krds-btn secondary medium egov-btn">취소</a>
            <button type="submit" class="krds-btn primary medium egov-btn">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
