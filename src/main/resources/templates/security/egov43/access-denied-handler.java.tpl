package ${packageName}.sec.handler;

import ${javaxOrJakarta}.servlet.ServletException;
import ${javaxOrJakarta}.servlet.http.HttpServletRequest;
import ${javaxOrJakarta}.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * eGovFrame 표준 접근 거부 핸들러 (HTTP 403)
 * 권한 없는 URL 접근 시 accessDenied 페이지로 리다이렉트
 */
@Component
public class EgovAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        response.sendRedirect(
            request.getContextPath() + "/cmm/error/accessDenied.do");
    }
}
