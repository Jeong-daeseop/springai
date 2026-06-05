package com.krdevops.springai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "egov")
public class EgovProperties {

    private Output output = new Output();

    @Getter
    @Setter
    public static class Output {
        private String basePath = System.getProperty("user.home") + "/Desktop/egov-generated";
    }
}
