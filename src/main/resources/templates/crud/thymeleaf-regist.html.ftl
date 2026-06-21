<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 등록</title>
</head>
<th:block layout:fragment="content">
<div class="d-flex justify-content-between align-items-center mb-3">
    <div>
        <h4 class="mb-1">${domainKr} 등록</h4>
        <nav aria-label="breadcrumb">
            <ol class="breadcrumb mb-0">
                <li class="breadcrumb-item"><a th:href="@{/}">홈</a></li>
                <li class="breadcrumb-item"><a th:href="@{${urlPrefix}List.do}">${domainKr} 목록</a></li>
                <li class="breadcrumb-item active">등록</li>
            </ol>
        </nav>
    </div>
</div>

<div class="card">
    <div class="card-header"><strong>정보 입력</strong></div>
    <div class="card-body">
        <form th:object="${'$'}{${domainLc}VO}" th:action="@{${urlPrefix}Regist.do}" method="post"
              class="needs-validation" novalidate>
            <div class="row g-3">
<#list fields as f>
                <div class="col-md-6">
                    <label for="${f.javaName}" class="form-label">${f.comment}<#if f.required> <span class="text-danger">*</span></#if></label>
                    <input type="text" th:field="*{${f.javaName}}" id="${f.javaName}"
                           class="krds-input"
                           <#if f.maxLength??>maxlength="${f.maxLength}"</#if>
                           placeholder="${f.comment}을(를) 입력하세요"/>
                    <p class="form-hint-invalid" th:if="${'$'}{#fields.hasErrors('${f.javaName}')}"
                       th:errors="*{${f.javaName}}"></p>
                </div>
</#list>
            </div>

            <div class="btn-area d-flex gap-2 mt-4">
                <a th:href="@{${urlPrefix}List.do}" class="krds-btn secondary medium">취소</a>
                <button type="submit" class="krds-btn primary medium">
                    <i class="bi bi-floppy me-1"></i>저장
                </button>
            </div>
        </form>
    </div>
</div>
</th:block>
</html>
