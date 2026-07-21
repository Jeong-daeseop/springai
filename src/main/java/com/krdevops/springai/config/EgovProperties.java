package com.krdevops.springai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "egov")
public class EgovProperties {

    private Output output = new Output();
    private Validation validation = new Validation();

    @Getter
    @Setter
    public static class Output {
        private String basePath = System.getProperty("user.home") + "/workspace-egov/egov-generated";
        private List<String> allowedPaths = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class Validation {
        /** 생성 프로젝트의 빌드 파일 실행은 명시적으로 활성화해야 한다. */
        private boolean allowBuildExecution = false;
        private int buildTimeoutSeconds = 180;
        private int maxOutputChars = 20000;
    }
}
