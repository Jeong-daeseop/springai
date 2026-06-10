package ${packageName}.sec.filter;

import org.springframework.web.filter.GenericFilterBean;
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import ${javaxOrJakarta}.servlet.http.HttpSession;

import java.io.IOException;

/**
 * eGovFrame 로그아웃 필터 (bopr 방식)
 *
 * web.xml 선언 순서: springSecurityFilterChain 다음에 위치
 * 적용 경로: /uat/uia/actionLogout.do
 *
 * 처리 순서:
 *   1. session.setAttribute("loginVO", null) — 세션 loginVO 초기화
 *   2. /egov_security_logout 리다이렉트 → Spring Security 내부 로그아웃 처리
 *      (EgovSecurityConfig logoutUrl=/uat/uia/actionLogout.do 와 협력)
 *
 * ⚠️ Spring Security logoutSuccessUrl 설정값으로 최종 리다이렉트됨
 *    context-security.xml logoutSuccessUrl 프로퍼티 확인
 */
public class EgovSpringSecurityLogoutFilter extends GenericFilterBean {

    private static final String LOGOUT_URL  = "/uat/uia/actionLogout.do";
    private static final String EGOV_LOGOUT = "/egov_security_logout";

    @Override
    public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                         ${javaxOrJakarta}.servlet.ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (!request.getRequestURI().endsWith(LOGOUT_URL)) {
            chain.doFilter(request, response);
            return;
        }

        // 세션 loginVO 초기화
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("loginVO", null);
        }

        // Spring Security 내부 로그아웃으로 위임
        response.sendRedirect(request.getContextPath() + EGOV_LOGOUT);
    }
}
