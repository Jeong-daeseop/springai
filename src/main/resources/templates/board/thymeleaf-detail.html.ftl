<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 상세</title>
</head>
<section layout:fragment="content">
    <th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #1e2124;">
        <h1>${domainKr} 상세</h1>
        <div style="display:flex;gap:8px;">
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
    <div style="margin-top:24px;border:1px solid #e8eaec;border-radius:6px;overflow:hidden;">
        <div style="display:grid;grid-template-columns:140px 1fr;">
            <div style="padding:14px 16px;background:#f6f7f8;font-weight:700;color:#464c53;">첨부파일</div>
            <div style="padding:14px 16px;">
                <a th:if="${r"${result.atchFileId != null and result.atchFileId != ''}"}"
                   th:href="@{${urlPrefix}FileDownload.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"},${atchFileId.javaName}=${r"${result.atchFileId}"})}"
                   style="display:inline-flex;align-items:center;gap:8px;color:#256ef4;font-weight:700;">
                    <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M12 3v12"></path>
                        <path d="m7 10 5 5 5-5"></path>
                        <path d="M5 21h14"></path>
                    </svg>
                    <span>첨부파일 다운로드</span>
                    <span style="font-size:13px;color:#8a949e;font-weight:500;"
                          th:text="${r"${result.atchFileId}"}">FILE_ID</span>
                </a>
                <span th:unless="${r"${result.atchFileId != null and result.atchFileId != ''}"}"
                      style="font-size:13px;color:#8a949e;">첨부파일 없음</span>
            </div>
        </div>
    </div>
</#if>

    <nav style="margin-top:24px;border:1px solid #e8eaec;border-radius:8px;overflow:hidden;" aria-label="이전글/다음글">
        <dl style="margin:0;">
            <div style="display:flex;align-items:stretch;border-bottom:1px solid #e8eaec;">
                <dt style="min-width:80px;padding:14px 16px;background:#f6f7f8;font-size:13px;font-weight:700;color:#464c53;display:flex;align-items:center;flex:none;">이전글</dt>
                <dd style="flex:1;margin:0;padding:14px 16px;display:flex;align-items:center;">
                    <a th:if="${r"${prevPost != null}"}"
                       th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${prevPost."}${nttId.javaName}${r"}"})}"
                       th:text="${r"${prevPost.nttSj}"}"
                       style="color:#256ef4;font-weight:500;">이전글 제목</a>
                    <span th:unless="${r"${prevPost != null}"}" style="color:#8a949e;font-size:13px;">이전글이 없습니다.</span>
                </dd>
            </div>
            <div style="display:flex;align-items:stretch;">
                <dt style="min-width:80px;padding:14px 16px;background:#f6f7f8;font-size:13px;font-weight:700;color:#464c53;display:flex;align-items:center;flex:none;">다음글</dt>
                <dd style="flex:1;margin:0;padding:14px 16px;display:flex;align-items:center;">
                    <a th:if="${r"${nextPost != null}"}"
                       th:href="@{${urlPrefix}Detail.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${nextPost."}${nttId.javaName}${r"}"})}"
                       th:text="${r"${nextPost.nttSj}"}"
                       style="color:#256ef4;font-weight:500;">다음글 제목</a>
                    <span th:unless="${r"${nextPost != null}"}" style="color:#8a949e;font-size:13px;">다음글이 없습니다.</span>
                </dd>
            </div>
        </dl>
    </nav>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:28px;">
        <a th:href="@{${urlPrefix}List.do(bbsId=${r"${result."}${bbsId.javaName}${r"}"})}"
           class="krds-btn secondary medium">목록</a>
        <div style="display:flex;gap:8px;">
            <a th:href="@{${urlPrefix}UpdtView.do(${bbsId.javaName}=${r"${result."}${bbsId.javaName}${r"}"},${nttId.javaName}=${r"${result."}${nttId.javaName}${r"}"})}"
               class="krds-btn primary medium">수정</a>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('deleteModal').showModal()">삭제</button>
        </div>
    </div>

    <dialog id="deleteModal"
            style="border:none;border-radius:12px;padding:32px;min-width:320px;max-width:400px;box-shadow:0 8px 32px rgba(0,0,0,.18);">
        <h2 style="margin:0 0 10px;font-size:18px;font-weight:800;color:#1e2124;">삭제 확인</h2>
        <p style="margin:0 0 24px;font-size:14px;color:#58616a;line-height:1.6;">삭제하시겠습니까?<br>삭제된 데이터는 복구할 수 없습니다.</p>
        <div style="display:flex;justify-content:flex-end;gap:8px;">
            <button type="button" class="krds-btn secondary medium"
                    onclick="document.getElementById('deleteModal').close()">취소</button>
            <button type="button" class="krds-btn negative medium"
                    onclick="document.getElementById('deleteForm').submit()">삭제</button>
        </div>
    </dialog>
    <form id="deleteForm" th:action="@{${urlPrefix}Delete.do}" method="post" style="display:none;">
        <input type="hidden" th:name="${bbsId.javaName}" th:value="${r"${result."}${bbsId.javaName}${r"}"}"/>
        <input type="hidden" th:name="${nttId.javaName}" th:value="${r"${result."}${nttId.javaName}${r"}"}"/>
    </form>
</section>
</html>
