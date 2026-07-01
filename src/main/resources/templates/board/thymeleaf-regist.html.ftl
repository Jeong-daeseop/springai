<!DOCTYPE html>
<html lang="ko"
      xmlns:th="http://www.thymeleaf.org"
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/default}">
<head>
    <title>${domainKr} 등록</title>
</head>
<section layout:fragment="content">
    <th:block th:replace="~{layout/breadcrumb :: breadcrumb}"></th:block>

    <div style="display:flex;align-items:center;justify-content:space-between;gap:16px;padding-bottom:16px;margin-bottom:20px;border-bottom:2px solid #1e2124;">
        <h1>${domainKr} 등록</h1>
    </div>

    <div style="display:flex;align-items:center;gap:4px;justify-content:flex-end;margin-bottom:12px;font-size:13px;color:#6d7882;">
        <span style="margin-left:2px;color:#d9363e;font-weight:700;" aria-hidden="true">*</span>
        <span>표시는 필수 입력 항목입니다.</span>
    </div>

    <form th:action="@{${urlPrefix}Regist.do}" method="post">
        <input type="hidden" th:name="${bbsId.javaName}"
               th:value="${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"}"/>

        <div class="krds-table-wrap">
            <table class="tbl col">
                <caption>${domainKr} 등록 입력 폼</caption>
                <tbody>
<#list formFields as f>
                <tr>
                    <th scope="row">
                        <label for="${f.javaName}">${f.comment}</label>
                    </th>
                    <td>
<#if f.javaName == "nttCn">
                        <textarea class="krds-input"
                                  id="${f.javaName}"
                                  th:name="${f.javaName}"
                                  rows="12"
                                  placeholder="${f.comment}을(를) 입력하세요"
                                  style="width:100%;min-height:220px;resize:vertical;line-height:1.7;"
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

        <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:28px;">
            <a th:href="@{${urlPrefix}List.do(bbsId=${r"${"}${domainLc}${r"VO."}${bbsId.javaName}${r"}"})}"
               class="krds-btn secondary medium">취소</a>
            <button type="submit" class="krds-btn primary medium">
                <span aria-hidden="true">✓</span>
                저장
            </button>
        </div>
    </form>
</section>
</html>
