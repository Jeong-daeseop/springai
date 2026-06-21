<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 상세</title>
</head>
<th:block layout:fragment="content">
<div th:if="${'$'}{message}" class="alert alert-success alert-dismissible fade show" role="alert">
    <span th:text="${'$'}{message}"></span>
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
</div>
<div class="d-flex justify-content-between align-items-center mb-3">
    <div>
        <h4 class="mb-1">${domainKr} 상세</h4>
        <nav aria-label="breadcrumb">
            <ol class="breadcrumb mb-0">
                <li class="breadcrumb-item"><a th:href="@{/}">홈</a></li>
                <li class="breadcrumb-item"><a th:href="@{${urlPrefix}List.do}">${domainKr} 목록</a></li>
                <li class="breadcrumb-item active">상세</li>
            </ol>
        </nav>
    </div>
</div>

<div class="card mb-3">
    <div class="card-header"><strong>${domainKr} 정보</strong></div>
    <div class="card-body p-0">
        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${domainKr} 상세 정보</caption>
                <colgroup>
                    <col style="width: 25%;">
                    <col>
                </colgroup>
                <tbody>
<#list fields as f>
                <tr>
                    <th scope="row">${f.comment}</th>
                    <td th:text="${'$'}{result.${f.javaName}}"></td>
                </tr>
</#list>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div class="btn-area d-flex gap-2">
    <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">
        <i class="bi bi-list me-1"></i>목록
    </a>
    <a th:href="@{${urlPrefix}UpdtView.do(${pk.javaName}=${'$'}{result.${pk.javaName}})}"
       class="krds-btn primary medium">
        <i class="bi bi-pencil me-1"></i>수정
    </a>
    <form th:action="@{${urlPrefix}Delete.do}" method="post" style="display:inline;"
          onsubmit="return confirm('삭제하시겠습니까?');">
        <input type="hidden" name="${pk.javaName}" th:value="${'$'}{result.${pk.javaName}}"/>
        <button type="submit" class="krds-btn tertiary medium">
            <i class="bi bi-trash me-1"></i>삭제
        </button>
    </form>
</div>
</th:block>
</html>
