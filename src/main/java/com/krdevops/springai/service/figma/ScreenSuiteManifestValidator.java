package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.ScreenSuiteManifest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ScreenSuiteManifestValidator {

    public List<DesignSystemIssue> validate(ScreenSuiteManifest manifest, List<ActualScreen> actualScreens) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (manifest == null) {
            return List.of(issue("SCREEN_SUITE_MANIFEST_MISSING", "Screen Suite Manifest가 없습니다.", null));
        }
        Map<String, ScreenPattern> actual = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (ActualScreen screen : actualScreens == null ? List.<ActualScreen>of() : actualScreens) {
            if (actual.putIfAbsent(screen.screenId(), screen.pattern()) != null) duplicates.add(screen.screenId());
        }
        duplicates.forEach(id -> issues.add(issue("DUPLICATE_SCREEN_ID", "Screen ID가 중복되었습니다: " + id, id)));
        for (ScreenSuiteManifest.ExpectedScreen expected : manifest.screens()) {
            ScreenPattern pattern = actual.get(expected.screenId());
            if (expected.required() && pattern == null) {
                issues.add(issue("REQUIRED_SCREEN_MISSING", "필수 화면이 누락되었습니다: " + expected.screenId(),
                        expected.screenId()));
            } else if (pattern != null && pattern != expected.pattern()) {
                issues.add(issue("SCREEN_PATTERN_MISMATCH",
                        expected.screenId() + "의 Pattern이 다릅니다: " + pattern.code(), expected.screenId()));
            }
        }
        return List.copyOf(issues);
    }

    private DesignSystemIssue issue(String code, String message, String target) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, target);
    }

    public record ActualScreen(String screenId, ScreenPattern pattern) {}
}
