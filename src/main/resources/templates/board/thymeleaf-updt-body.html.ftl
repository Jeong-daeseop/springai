    <div class="egov-page-header">
        <h1 class="egov-page-title">${domainKr} 수정</h1>
    </div>

    <div class="egov-form-required-guide">
        <span class="egov-required-mark" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form th:action="@{${urlPrefix}Updt.do}" method="post">
        <input type="hidden" th:name="${bbsId.javaName}"
               th:value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>
        <input type="hidden" th:name="${nttId.javaName}"
               th:value="${r"${"}${domainLc}${r"VO."}${nttId.javaName}${r"}"}"/>

        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${domainKr} 수정 입력 폼</caption>
                <tbody>
<#list formFields as f>
                <tr>
                    <th scope="row">
                        <label for="${f.javaName}">${f.comment}</label>
                    </th>
                    <td>
<#if f.javaName == "nttCn">
                        <textarea class="krds-input egov-textarea"
                                  id="${f.javaName}"
                                  th:name="${f.javaName}"
                                  rows="12"
                                  placeholder="${f.comment}을(를) 입력하세요"
                                  th:text="${r"${"}${domainLc}${r"VO."}${f.javaName}${r"}"}"></textarea>
<#else>
                        <input type="text"
                               class="krds-input"
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
            <a th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${"}${domainLc}${r"VO."}${nttId.javaName}${r"}"})}"
               class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
