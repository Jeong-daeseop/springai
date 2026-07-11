    <div class="egov-page-header">
        <h1 class="egov-page-title">${domainKr} 상세</h1>
        <div class="egov-button-group">
            <a th:href="@{${urlPrefix}List.do(bbsId=${r"${result."}${bbsId.javaName}${r"}"})}"
               class="krds-btn secondary medium">목록</a>
            <a th:href="@{${urlPrefix}UpdtView.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"})}"
               class="krds-btn primary medium">수정</a>
        </div>
    </div>

    <div class="krds-table-wrap">
        <table class="tbl col">
            <caption>${domainKr} 상세 정보</caption>
            <tbody>
<#list fields as f>
            <tr>
                <th scope="row">${f.comment}</th>
                <td th:text="${r"${result."}${f.javaName}${r"}"}"></td>
            </tr>
</#list>
            </tbody>
        </table>
    </div>

<#if hasFile>
    <div class="egov-attachment-box">
        <div class="egov-definition-grid">
            <div class="egov-definition-label">첨부파일</div>
            <div class="egov-definition-value">
                <a th:if="${r"${result.atchFileId != null and result.atchFileId != ''}"}"
                   th:href="@{${urlPrefix}FileDownload.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"},${atchFileId.javaName}=${r"${result.atchFileId}"})}"
                   class="egov-file-detail-link">
                    <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M12 3v12"></path>
                        <path d="m7 10 5 5 5-5"></path>
                        <path d="M5 21h14"></path>
                    </svg>
                    <span>첨부파일 다운로드</span>
                    <span class="egov-file-id"
                          th:text="${r"${result.atchFileId}"}">FILE_ID</span>
                </a>
                <span th:unless="${r"${result.atchFileId != null and result.atchFileId != ''}"}"
                      class="egov-file-empty">첨부파일 없음</span>
            </div>
        </div>
    </div>
</#if>

    <nav class="egov-post-nav" aria-label="이전글/다음글">
        <dl class="egov-post-nav-list">
            <div class="egov-post-nav-row">
                <dt class="egov-post-nav-term">이전글</dt>
                <dd class="egov-post-nav-desc">
                    <a th:if="${r"${prevPost != null}"}"
                       th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${prevPost."}${nttId.javaName}${r"}"})}"
                       th:text="${r"${prevPost.nttSj}"}"
                       class="egov-post-nav-link">이전글 제목</a>
                    <span th:unless="${r"${prevPost != null}"}" class="egov-post-nav-empty">이전글이 없습니다.</span>
                </dd>
            </div>
            <div class="egov-post-nav-row">
                <dt class="egov-post-nav-term">다음글</dt>
                <dd class="egov-post-nav-desc">
                    <a th:if="${r"${nextPost != null}"}"
                       th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${nextPost."}${nttId.javaName}${r"}"})}"
                       th:text="${r"${nextPost.nttSj}"}"
                       class="egov-post-nav-link">다음글 제목</a>
                    <span th:unless="${r"${nextPost != null}"}" class="egov-post-nav-empty">다음글이 없습니다.</span>
                </dd>
            </div>
        </dl>
    </nav>

    <div class="egov-form-actions">
        <a th:href="@{${urlPrefix}List.do(bbsId=${r"${result."}${bbsId.javaName}${r"}"})}"
           class="krds-btn secondary medium">목록</a>
        <div class="egov-button-group">
            <a th:href="@{${urlPrefix}UpdtView.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"})}"
               class="krds-btn primary medium">수정</a>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('deleteModal').showModal()">삭제</button>
        </div>
    </div>

    <dialog id="deleteModal" class="egov-modal">
        <h2 class="egov-modal-title">삭제 확인</h2>
        <p class="egov-modal-desc">삭제하시겠습니까?<br>삭제된 데이터는 복구할 수 없습니다.</p>
        <div class="egov-modal-actions">
            <button type="button" class="krds-btn secondary medium"
                    onclick="document.getElementById('deleteModal').close()">취소</button>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('deleteForm').submit()">삭제</button>
        </div>
    </dialog>
    <form id="deleteForm" th:action="@{${urlPrefix}Delete.do}" method="post" class="egov-hidden">
        <input type="hidden" th:name="${bbsId.javaName}" th:value="${r"${result."}${bbsId.javaName}${r"}"}"/>
        <input type="hidden" th:name="${nttId.javaName}" th:value="${r"${result."}${nttId.javaName}${r"}"}"/>
    </form>
