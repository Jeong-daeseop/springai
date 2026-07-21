    <div class="egov-page-header">
        <h1 class="egov-page-title">${master.domainKr} 상세</h1>
        <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium egov-btn">목록</a>
    </div>

    <div id="toast-alert" th:if="${'$'}{message}" class="egov-toast" role="alert" aria-live="polite">
        <span aria-hidden="true" class="egov-toast-icon">✓</span>
        <span th:text="${'$'}{message}">처리되었습니다.</span>
    </div>
    <script>
    (function(){var t=document.getElementById('toast-alert');if(t){setTimeout(function(){t.style.transition='opacity 0.4s';t.style.opacity='0';setTimeout(function(){t.remove();},400);},3000);}})();
    </script>

    <section class="egov-section">
        <h2 class="egov-section-title">${master.domainKr} 정보</h2>
        <div class="krds-table-wrap">
            <table class="tbl col egov-form-table">
                <caption>${master.domainKr} 상세 정보</caption>
                <tbody>
<#list master.fields as f>
<#assign isStatus = f.javaName == "useAt" || f.javaName == "useYn" || f.javaName == "sttus" || f.javaName == "status" || f.javaName == "activeYn">
                <tr>
                    <th scope="row">${f.comment}</th>
                    <td>
<#if isStatus>
                        <span th:if="${'$'}{result.${f.javaName} == 'Y'}"
                              class="egov-status-badge positive">사용</span>
                        <span th:unless="${'$'}{result.${f.javaName} == 'Y'}"
                              class="egov-status-badge negative">중지</span>
<#else>
                        <span th:text="${'$'}{result.${f.javaName}}"></span>
</#if>
                    </td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>
    </section>

    <section class="egov-section">
        <div class="egov-section-header">
            <h2 class="egov-section-title">
                ${detail.domainKr} 목록
                <span th:if="${'$'}{detailList != null}"
                      class="egov-section-count"
                      th:text="'총 ' + ${'$'}{#lists.size(detailList)} + '건'">총 0건</span>
            </h2>
            <a th:href="@{${urlPrefix}${detail.domainLc?cap_first}RegistView.do(${master.pk.javaName}=${'$'}{result.${master.pk.javaName}})}"
               class="krds-btn primary small egov-btn">
                <span aria-hidden="true">＋</span>
                ${detail.domainKr} 등록
            </a>
        </div>
        <div class="krds-table-wrap">
            <table class="tbl col egov-list-table">
                <caption>${detail.domainKr} 목록</caption>
                <thead>
                <tr>
<#list detail.fields as f>
                    <th scope="col">${f.comment}</th>
</#list>
                    <th scope="col">관리</th>
                </tr>
                </thead>
                <tbody>
                <tr th:each="detailItem : ${'$'}{detailList}">
<#list detail.fields as f>
<#assign isStatus = f.javaName == "useAt" || f.javaName == "useYn" || f.javaName == "sttus" || f.javaName == "status" || f.javaName == "activeYn">
                    <td>
<#if isStatus>
                        <span th:if="${'$'}{detailItem.${f.javaName} == 'Y'}"
                              class="egov-status-badge positive">사용</span>
                        <span th:unless="${'$'}{detailItem.${f.javaName} == 'Y'}"
                              class="egov-status-badge negative">중지</span>
<#else>
                        <span th:text="${'$'}{detailItem.${f.javaName}}"></span>
</#if>
                    </td>
</#list>
                    <td class="egov-table-actions">
                        <a th:href="@{${urlPrefix}${detail.domainLc?cap_first}UpdtView.do(${master.pk.javaName}=${'$'}{result.${master.pk.javaName}},${detail.pk.javaName}=${'$'}{detailItem.${detail.pk.javaName}})}"
                           class="krds-btn secondary small egov-btn">수정</a>
                    </td>
                </tr>
                <tr th:if="${'$'}{#lists.isEmpty(detailList)}">
                    <td colspan="${detail.fields?size + 1}" class="egov-empty-cell compact">
                        등록된 ${detail.domainKr} 정보가 없습니다.
                    </td>
                </tr>
                </tbody>
            </table>
        </div>
    </section>

    <div class="egov-form-actions">
        <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium egov-btn">목록</a>
        <div class="egov-button-group">
            <a th:href="@{${urlPrefix}UpdtView.do(${master.pk.javaName}=${'$'}{result.${master.pk.javaName}})}"
               class="krds-btn primary medium egov-btn">수정</a>
            <button type="button" class="krds-btn negative medium egov-btn"
                    onclick="document.getElementById('deleteModal').showModal()">삭제</button>
        </div>
    </div>

    <dialog id="deleteModal" class="egov-modal">
        <h2 class="egov-modal-title">삭제 확인</h2>
        <p class="egov-modal-desc">삭제하시겠습니까?<br>삭제된 데이터는 복구할 수 없습니다.</p>
        <div class="egov-modal-actions">
            <button type="button" class="krds-btn secondary medium egov-btn"
                    onclick="document.getElementById('deleteModal').close()">취소</button>
            <button type="button" class="krds-btn negative medium egov-btn"
                    onclick="document.getElementById('deleteForm').submit()">삭제</button>
        </div>
    </dialog>
    <form id="deleteForm" th:action="@{${urlPrefix}Delete.do}" method="post" class="egov-hidden">
        <input th:if="${'$'}{_csrf != null}" type="hidden"
               th:name="${'$'}{_csrf.parameterName}" th:value="${'$'}{_csrf.token}"/>
        <input type="hidden" name="${master.pk.javaName}" th:value="${'$'}{result.${master.pk.javaName}}"/>
    </form>
