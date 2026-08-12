package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.design.role.ScreenPattern;

import java.util.List;

public record ScreenSuiteManifest(String suiteId, String version, List<ExpectedScreen> screens) {
    public ScreenSuiteManifest {
        if (suiteId == null || suiteId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Suite id와 version은 필수입니다.");
        }
        screens = screens == null ? List.of() : List.copyOf(screens);
    }

    public record ExpectedScreen(String screenId, ScreenPattern pattern, boolean required) {
        public ExpectedScreen {
            if (screenId == null || screenId.isBlank() || pattern == null) {
                throw new IllegalArgumentException("Expected Screen의 id와 pattern은 필수입니다.");
            }
        }
    }
}
