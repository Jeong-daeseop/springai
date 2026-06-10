package ${packageName}.sec.handler;

import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * eGovFrame 표준 로그인 성공 핸들러
 * 로그인 성공 후 defaultTargetUrl 로 리다이렉트
 * 필요 시 로그인 이력 저장, 세션 정보 설정 등 추가
 */
public class EgovAuthenticationSuccessHandler
        extends SimpleUrlAuthenticationSuccessHandler {

    public EgovAuthenticationSuccessHandler(String defaultTargetUrl) {
        super(defaultTargetUrl);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
            HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        // 필요 시 로그인 이력 저장 등 추가
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
