package ${packageName}.sec.filter;

import org.springframework.web.filter.GenericFilterBean;
import ${javaxOrJakarta}.servlet.FilterChain;
import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import ${javaxOrJakarta}.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.io.IOException;

/**
 * eGovFrame DB 인증 핵심 필터 (bopr 방식)
 *
 * web.xml 선언 순서: CharacterEncodingFilter → HTMLTagFilter → LoginPolicyFilter
 *                   → 이 필터 → springSecurityFilterChain → EgovSpringSecurityLogoutFilter
 *
 * Spring Security formLogin()을 우회하여 직접 DB 인증 수행:
 *   1. /uat/uia/actionLogin.do POST 요청 감지
 *   2. 비밀번호 길이 검증 (8자 미만 or 20자 초과 → 로그인 화면 forward)
 *   3. UserDetailsService.loadUserByUsername() → 사용자/권한 조회
 *   4. 비밀번호 검증 (프로젝트 암호화 방식에 맞게 구현)
 *   5. 인증 성공: SecurityContextHolder 설정 + 세션 저장 → 메인 화면 리다이렉트
 *   6. 인증 실패: 로그인 화면 forward + 에러 메시지
 *
 * ⚠️ web.xml에서 반드시 springSecurityFilterChain 앞에 선언
 * ⚠️ verifyPassword() 구현 필수 — 프로젝트 암호화 방식에 맞게 교체
 *    bopr: EgovFileScrty.encryptPassword(rawPassword, userId) → SHA-256+Base64+salt
 *    일반: passwordEncoder.matches(rawPassword, encodedPassword)
 */
public class EgovSpringSecurityLoginFilter extends GenericFilterBean {

    private static final String LOGIN_URL  = "/uat/uia/actionLogin.do";
    private static final String LOGIN_VIEW = "/WEB-INF/jsp/uat/uia/egovLoginUsr.jsp";
    private static final String MAIN_URL   = "/main/Main.do"; // ⚠️ 프로젝트 메인 URL로 변경

    private final UserDetailsService userDetailsService;
    private final HttpSessionSecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public EgovSpringSecurityLoginFilter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void doFilter(${javaxOrJakarta}.servlet.ServletRequest req,
                         ${javaxOrJakarta}.servlet.ServletResponse res,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (!isLoginRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        String userId   = request.getParameter("j_username");
        String password = request.getParameter("j_password");

        // 비밀번호 길이 검증 (8자 미만 or 20자 초과)
        if (password == null || password.length() < 8 || password.length() > 20) {
            request.setAttribute("loginMessage", "비밀번호는 8~20자로 입력하세요.");
            request.getRequestDispatcher(LOGIN_VIEW).forward(request, response);
            return;
        }

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

            if (!verifyPassword(password, userId, userDetails.getPassword())) {
                request.setAttribute("loginMessage", "아이디 또는 비밀번호가 올바르지 않습니다.");
                request.getRequestDispatcher(LOGIN_VIEW).forward(request, response);
                return;
            }

            // SecurityContext 생성 및 SecurityContextHolder 설정
            UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                    userDetails, null, userDetails.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // 세션에 SecurityContext 저장
            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);

            // ⚠️ LoginVO 세션 저장 — 프로젝트 LoginVO 구성 후 아래 주석 해제
            // session.setAttribute("loginVO", loginVO);

            response.sendRedirect(request.getContextPath() + MAIN_URL);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            request.setAttribute("loginMessage", "인증 처리 중 오류가 발생했습니다.");
            request.getRequestDispatcher(LOGIN_VIEW).forward(request, response);
        }
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
            && request.getRequestURI().endsWith(LOGIN_URL);
    }

    /**
     * 비밀번호 검증 — 프로젝트 암호화 방식에 맞게 구현 필수
     *
     * bopr (SHA-256 + Base64 + userId salt):
     *   return EgovFileScrty.encryptPassword(rawPassword, userId).equals(encodedPassword);
     *
     * BCrypt:
     *   return passwordEncoder.matches(rawPassword, encodedPassword);
     */
    private boolean verifyPassword(String rawPassword, String userId, String encodedPassword) {
        // TODO: 프로젝트 비밀번호 검증 로직으로 교체
        throw new UnsupportedOperationException(
            "verifyPassword() 구현 필요 — 프로젝트 암호화 방식에 맞게 교체하세요.");
    }
}
