<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c"      uri="http://java.sun.com/jsp/jstl/core"%>
<%@ taglib prefix="ui"     uri="http://egovframework.gov/ctl/ui"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags"%>
<%
    String contextPath = request.getContextPath();
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>EMPLYRINFO 목록</title>
    <link rel="stylesheet" href="<%=contextPath%>/resources/css/styles.css">
</head>
<body>
<div class="container">
    <h2 class="page-title">EMPLYRINFO 목록</h2>

    <c:if test="${not empty message}">
    <div class="alert alert-success">
        <c:out value="${message}"/>
    </div>
    </c:if>

    <!-- 검색 -->
    <div class="fieldset">
        <form name="searchForm" action="<c:url value='/emp/employerList.do'/>" method="get">
            <input type="hidden" name="pageIndex" value="${searchVO.pageIndex}"/>
            <div class="form-group">
                <div class="form-conts keyword-sch">
                    <div class="sch-form-wrap">
                        <div class="input-group">
                            <select name="searchCondition" class="krds-form-select medium" title="검색조건">
                                <option value="1">EMPLYRINFOID</option>
                            </select>
                            <div class="sch-input">
                                <input type="text" name="searchKeyword" class="krds-input medium"
                                       value="${searchVO.searchKeyword}" placeholder="검색어를 입력하세요"/>
                            </div>
                            <button type="submit" class="krds-btn primary medium">검색</button>
                        </div>
                    </div>
                </div>
            </div>
        </form>
    </div>

    <!-- 목록 -->
    <div class="krds-structured-list-table egov-density-standard">
        <div class="search-list-top">
            <div class="sch-left"></div>
            <div class="sch-right">
                <a href="<c:url value='/emp/employerRegistView.do'/>" class="krds-btn primary medium">등록</a>
            </div>
        </div>

        <div class="krds-table-wrap">
            <table class="tbl col data">
                <caption>EMPLYRINFO 목록 표</caption>
                <colgroup>
                    <col class="egov-col-narrow">
                    <col>
                    <col>
                    <col>
                    <col>
                    <col>
                    <col>
                    <col class="egov-col-action-percent">
                </colgroup>
                <thead>
                <tr>
                    <th scope="col">번호</th>
                    <th scope="col">직원ID</th>
                    <th scope="col">최초등록시점</th>
                    <th scope="col">최종수정시점</th>
                    <th scope="col">직급명</th>
                    <th scope="col">이메일주소</th>
                    <th scope="col">직원명</th>
                    <th scope="col">관리</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="item" items="${resultList}" varStatus="status">
                    <tr>
                        <td>${paginationInfo.totalRecordCount - ((searchVO.pageIndex-1) * searchVO.pageUnit) - status.index}</td>
                        <td><c:out value="${item.emplyrId}"/></td>
                        <td><c:out value="${item.frstRegistPnttm}"/></td>
                        <td><c:out value="${item.lastUpdtPnttm}"/></td>
                        <td><c:out value="${item.ofcpsNm}"/></td>
                        <td><c:out value="${item.emailAdres}"/></td>
                        <td><c:out value="${item.emplyrNm}"/></td>
                        <td>
                            <a href="<c:url value='/emp/employerDetail.do'/>?emplyrId=${item.emplyrId}"
                               class="krds-btn secondary xsmall">상세</a>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty resultList}">
                    <tr>
                        <td colspan="8" class="no-data">조회된 데이터가 없습니다.</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>

        <!-- 페이징 -->
        <div class="krds-pagination">
            <ui:pagination paginationInfo="${paginationInfo}"
                           type="image"
                           jsFunction="linkPage"/>
        </div>
    </div>
</div>

<script>
function linkPage(pageNo) {
    document.searchForm.pageIndex.value = pageNo;
    document.searchForm.submit();
}
</script>
<script src="<%=contextPath%>/resources/js/krds.min.js"></script>
</body>
</html>
