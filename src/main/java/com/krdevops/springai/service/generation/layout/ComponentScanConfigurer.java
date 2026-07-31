package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 기존 @Controller component-scan base-package를 절대 좁히지 않는다 — packageName을
 * 그대로 덮어쓰면 MainController 등 다른 패키지의 기존 컨트롤러가 스캔 대상에서
 * 빠져 404가 발생한다. requiredBasePackage/mergeBasePackages는 MyBatisRuntimeConfigurer가
 * MapperScannerConfigurer basePackage 병합에 쓰는 것과 동일한 로직으로, 기존 범위를
 * 포함하는 방향으로만 넓힌다.
 */
@Component
@RequiredArgsConstructor
public class ComponentScanConfigurer {

    private static final Pattern COMPONENT_SCAN_BASE_PACKAGE_PATTERN = Pattern.compile(
            "<context:component-scan\\b([^>]*?\\bbase-package\\s*=\\s*\")([^\"]*)(\"[^>]*>)",
            Pattern.DOTALL);

    private final MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;

    public ComponentScanPatch patch(String content, String packageName, Path servletContextPath) {
        Matcher matcher = COMPONENT_SCAN_BASE_PACKAGE_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new ComponentScanPatch(
                    content,
                    "  확인 필요: " + servletContextPath
                            + " 에 <context:component-scan base-package=\"...\">가 없어 "
                            + "GNB 컴포넌트 스캔 범위를 자동 확인하지 못했습니다.\n",
                    false);
        }

        String existingBasePackages = matcher.group(2).trim();
        String requiredBasePackage = myBatisRuntimeConfigurer.requiredBasePackage(packageName);
        if (myBatisRuntimeConfigurer.packagesCover(existingBasePackages, requiredBasePackage)) {
            return new ComponentScanPatch(
                    content,
                    "  보존: " + servletContextPath + " component-scan base-package="
                            + existingBasePackages + " (GNB servlet 스캔 범위 이미 포함)\n",
                    false);
        }

        String mergedBasePackage = myBatisRuntimeConfigurer.mergeBasePackages(existingBasePackages, requiredBasePackage);
        String patchedContent = content.substring(0, matcher.start(2))
                + mergedBasePackage
                + content.substring(matcher.end(2));
        return new ComponentScanPatch(
                patchedContent,
                "  변경: " + servletContextPath + " component-scan base-package="
                        + mergedBasePackage + " (기존 스캔 범위를 포함하도록 확장)\n",
                true);
    }

    public record ComponentScanPatch(String content, String message, boolean changed) {
    }
}
