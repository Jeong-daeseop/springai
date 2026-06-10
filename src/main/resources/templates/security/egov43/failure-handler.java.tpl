package ${packageName}.sec.handler;

import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/**
 * eGovFrame 표준 로그인 실패 핸들러
 * 인증 실패 시 defaultFailureUrl 로 리다이렉트
 * 필요 시 실패 이력 저장, 계정 잠금 처리 등 추가
 */
public class EgovAuthenticationFailureHandler
        extends SimpleUrlAuthenticationFailureHandler {

    public EgovAuthenticationFailureHandler(String defaultFailureUrl) {
        super(defaultFailureUrl);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        // 필요 시 실패 횟수 기록, 계정 잠금 처리 등 추가
        super.onAuthenticationFailure(request, response, exception);
    }
}
