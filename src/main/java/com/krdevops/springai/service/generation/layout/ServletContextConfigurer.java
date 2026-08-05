package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * WAR 프로젝트의 servlet-context.xml에 EgovGnbMenuInterceptor 등록 블록을 patch한다.
 * 파일을 새로 만들지 않는다 — servlet-context.xml 최초 생성은 ProjectInitializrTool 전속.
 * Boot 프로젝트(파일 자체가 없음)와 WAR 비표준 구조는 조용히 skip하고 안내만 반환한다(1차 구현 범위).
 *
 * <p>WP7 5차 pass: 저장은 {@code Files.writeString} 원시 호출 대신 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#ATOMIC_APPROVED})로 위임한다.
 */
@Component
@RequiredArgsConstructor
public class ServletContextConfigurer {

    private static final String SERVLET_CONTEXT_XML_RELATIVE_PATH =
            "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml";

    private final ComponentScanConfigurer componentScanConfigurer;
    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;
    private final OperationHashFactory hashFactory;

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
        String original;
        try {
            original = Files.readString(servletContextPath);
        } catch (IOException e) {
            return new ServletContextPatchResult(
                    "  실패: " + servletContextPath + " 읽기 오류 — " + e.getMessage() + "\n", true);
        }

        ComponentScanConfigurer.ComponentScanPatch componentScanPatch =
                componentScanConfigurer.patch(original, packageName, servletContextPath);
        String content = componentScanPatch.content();

        if (content.contains(interceptorClass)) {
            if (componentScanPatch.changed()) {
                String writeFailure = writeChange(outputPath, original, content);
                if (writeFailure != null) {
                    return new ServletContextPatchResult(
                            "  실패: " + servletContextPath + " 쓰기 오류 — " + writeFailure + "\n", true);
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
        String writeFailure = writeChange(outputPath, original, patched);
        if (writeFailure != null) {
            return new ServletContextPatchResult(
                    "  실패: " + servletContextPath + " 쓰기 오류 — " + writeFailure + "\n", true);
        }
        return new ServletContextPatchResult(
                componentScanPatch.message()
              + "  등록: " + servletContextPath + " 에 EgovGnbMenuInterceptor patch 완료 "
              + "(bean class=" + interceptorClass + ", autowire=constructor)\n",
                false);
    }

    /** @return 실패 상세 메시지, 성공하면 {@code null} */
    private String writeChange(Path outputPath, String before, String after) {
        codeService.validateOutputRoot(outputPath.toString());
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath.toString(), null,
                List.of(new ProjectChangeSet.FileChange(
                        SERVLET_CONTEXT_XML_RELATIVE_PATH,
                        hashFactory.sha256Hex(before.getBytes(StandardCharsets.UTF_8)), after, null)),
                List.of(), ProjectWritePolicy.ATOMIC_APPROVED);
        ApplyOutcome outcome = writePort.apply(changeSet);
        if (outcome.status() == ApplyOutcome.Status.APPLIED) {
            return null;
        }
        return switch (outcome.status()) {
            case CONFLICT -> "적용 직전 파일이 변경됨: " + outcome.conflictingPaths();
            case ROLLED_BACK -> outcome.failureDetail();
            default -> "알 수 없는 결과: " + outcome.status();
        };
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
