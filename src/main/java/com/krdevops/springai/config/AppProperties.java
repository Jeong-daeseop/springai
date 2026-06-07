package com.krdevops.springai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private List<String> documentPaths = List.of(System.getProperty("user.home") + "/documents/egovframe-docs-main");
    private String apiKey;
    private List<String> openaiModels = List.of("gpt-4o-mini", "gpt-4o");
}
