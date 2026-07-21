    <div class="egov-page-header">
        <h1 class="egov-page-title">${domainKr} 상세</h1>
        <a th:href="@{${route.resolvedListPath()}}" class="krds-btn secondary medium egov-btn">목록</a>
    </div>

    <div id="toast-alert" th:if="${'$'}{message}" class="egov-toast" role="alert" aria-live="polite">
        <span aria-hidden="true" class="egov-toast-icon">✓</span>
        <span th:text="${'$'}{message}">처리되었습니다.</span>
    </div>
    <script>
    (function(){var t=document.getElementById('toast-alert');if(t){setTimeout(function(){t.style.transition='opacity 0.4s';t.style.opacity='0';setTimeout(function(){t.remove();},400);},3000);}})();
    </script>

    <section class="egov-section">
        <h2 class="egov-section-title">${domainKr} 정보</h2>
        <div class="krds-table-wrap">
            <table class="tbl col egov-form-table">
                <caption>${domainKr} 상세 정보</caption>
                <tbody>
<#list effectiveDetailFields as f>
                <tr>
                    <th scope="row">${f.comment}</th>
                    <td th:text="${'$'}{result.${f.javaName}}"></td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>
    </section>

    <div class="egov-form-actions">
        <a th:href="@{${route.resolvedListPath()}}" class="krds-btn secondary medium egov-btn">목록</a>
        <div class="egov-button-group">
            <a th:href="@{${route.resolvedUpdtViewPath()}(<#list pkFields as p>${p.javaName}=${'$'}{result.${p.javaName}}<#sep>,</#sep></#list>)}"
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
    <form id="deleteForm" th:action="@{${route.resolvedDeletePath()}}" method="post" class="egov-hidden">
        <input th:if="${'$'}{_csrf != null}" type="hidden"
               th:name="${'$'}{_csrf.parameterName}" th:value="${'$'}{_csrf.token}"/>
<#list pkFields as p>
        <input type="hidden" name="${p.javaName}" th:value="${'$'}{result.${p.javaName}}"/>
</#list>
    </form>
