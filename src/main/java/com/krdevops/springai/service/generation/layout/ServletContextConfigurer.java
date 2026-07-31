package com.krdevops.springai.service.generation.layout;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * WAR 프로젝트의 servlet-context.xml에 EgovGnbMenuInterceptor 등록 블록을 patch한다.
 * 파일을 새로 만들지 않는다 — servlet-context.xml 최초 생성은 ProjectInitializrTool 전속.
 * Boot 프로젝트(파일 자체가 없음)와 WAR 비표준 구조는 조용히 skip하고 안내만 반환한다(1차 구현 범위).
 */
@Component
@RequiredArgsConstructor
public class ServletContextConfigurer {

    private static final String SERVLET_CONTEXT_XML_RELATIVE_PATH =
            "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml";

    private final ComponentScanConfigurer componentScanConfigurer;

    public ServletContextPatchResult patch(Path outputPath, String packageName) {
        Path servletContextPath = Paths.get(outputPath.toString(), SERVLET_CONTEXT_XML_RELATIVE_PATH).normalize();
        if (!Files.exists(servletContextPath)) {
            return new ServletContextPatchResult(
                    "  건너뜀: " + servletContextPath + " 파일이 없습니다. "
                  + "Boot 프로젝트라면 정상입니다(1차 미지원 — WebMvcConfigurer 방식 별도 필요). "
                  + "WAR 프로젝트라면 initializeProject()로 먼저 생성했는지 확인하거나 "
                  + "<mvc:interceptors> 블록을 수동으로 추가하세요.\n",
                    false);
        }

        String interceptorClass = packageName + ".cmm.web.EgovGnbMenuInterceptor";
        String content;
        try {
            content = Files.readString(servletContextPath);
        } catch (IOException e) {
            return new ServletContextPatchResult(
                    "  실패: " + servletContextPath + " 읽기 오류 — " + e.getMessage() + "\n", true);
        }

        ComponentScanConfigurer.ComponentScanPatch componentScanPatch =
                componentScanConfigurer.patch(content, packageName, servletContextPath);
        content = componentScanPatch.content();

        if (content.contains(interceptorClass)) {
            if (componentScanPatch.changed()) {
                try {
                    Files.writeString(servletContextPath, content);
                } catch (IOException e) {
                    return new ServletContextPatchResult(
                            "  실패: " + servletContextPath + " 쓰기 오류 — " + e.getMessage() + "\n", true);
                }
            }
            return new ServletContextPatchResult(
                    componentScanPatch.message()
                  + "  보존: " + servletContextPath + " (EgovGnbMenuInterceptor 이미 등록됨)\n",
                    false);
        }

        int beansCloseCount = countOccurrences(content, "</beans>");
        if (beansCloseCount != 1) {
            return new ServletContextPatchResult(
                    "  실패: " + servletContextPath + " — </beans> 태그가 " + beansCloseCount
                  + "개 발견되어 안전하게 삽입할 수 없습니다. <mvc:interceptors> 블록을 수동으로 추가하세요.\n",
                    true);
        }

        String interceptorBlock = """

                <mvc:interceptors>
                    <mvc:interceptor>
                        <mvc:mapping path="/**"/>
                        <bean class="%s" autowire="constructor"/>
                    </mvc:interceptor>
                </mvc:interceptors>

                """.formatted(interceptorClass);

        String patched = content.replace("</beans>", interceptorBlock + "</beans>");
        try {
            Files.writeString(servletContextPath, patched);
        } catch (IOException e) {
            return new ServletContextPatchResult(
                    "  실패: " + servletContextPath + " 쓰기 오류 — " + e.getMessage() + "\n", true);
        }
        return new ServletContextPatchResult(
                componentScanPatch.message()
              + "  등록: " + servletContextPath + " 에 EgovGnbMenuInterceptor patch 완료 "
              + "(bean class=" + interceptorClass + ", autowire=constructor)\n",
                false);
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }

    public record ServletContextPatchResult(String message, boolean failed) {
    }
}
