package ${packageName}.uat.uap.filter;

import org.springframework.web.filter.GenericFilterBean;
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 로그인 정책 필터 — 비밀번호 만료 / 계정 잠금 사전 체크
 *
 * web.xml 선언 순서: HTMLTagFilter 다음, EgovSpringSecurityLoginFilter 이전
 * 적용 경로: /uat/uia/actionLogin.do (로그인 처리 URL만)
 *
 * 처리 순서:
 *   1. /actionLogin.do POST 요청만 처리
 *   2. userId로 계정 정책 조회 (비밀번호 만료일, 잠금 여부)
 *   3. 정책 위반 시 로그인 화면 forward (에러 메시지 포함)
 *   4. 정책 통과 시 다음 필터(EgovSpringSecurityLoginFilter)로 chain
 *
 * ⚠️ 계정 정책 테이블은 프로젝트별 상이 — DB 조회 로직 직접 구현 필요
 *    bopr: EgovLoginService / 계정 잠금 정책 테이블 사용
 *    COM계열: LETTNLOGINPOLICY 테이블 기반
 */
public class EgovLoginPolicyFilter extends GenericFilterBean {

    private static final String LOGIN_URL  = "/uat/uia/actionLogin.do";
    private static final String LOGIN_VIEW = "/WEB-INF/jsp/uat/uia/egovLoginUsr.jsp";

    @Override
    public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                         ${javaxOrJakarta}.servlet.ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (!isPolicyCheckTarget(request)) {
            chain.doFilter(request, response);
            return;
        }

        String userId = request.getParameter("j_username");

        // ⚠️ 계정 정책 체크 — 프로젝트 서비스/DB 로직으로 구현
        // boolean isExpired = loginPolicyService.isPasswordExpired(userId);
        // boolean isLocked  = loginPolicyService.isAccountLocked(userId);
        //
        // if (isExpired) {
        //     request.setAttribute("loginMessage", "비밀번호가 만료되었습니다. 변경 후 로그인하세요.");
        //     request.getRequestDispatcher(LOGIN_VIEW).forward(request, response);
        //     return;
        // }
        // if (isLocked) {
        //     request.setAttribute("loginMessage", "계정이 잠겼습니다. 관리자에게 문의하세요.");
        //     request.getRequestDispatcher(LOGIN_VIEW).forward(request, response);
        //     return;
        // }

        chain.doFilter(request, response);
    }

    private boolean isPolicyCheckTarget(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
            && request.getRequestURI().endsWith(LOGIN_URL);
    }
}
