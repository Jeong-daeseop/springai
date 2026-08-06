package com.krdevops.springai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

@Configuration
public class ExternalTemplateResolverConfig {

    @Bean
    @ConditionalOnExpression("'${app.templates.external-directory:}'.length() > 0")
    SpringResourceTemplateResolver externalTemplateResolver(
            ApplicationContext applicationContext,
            @org.springframework.beans.factory.annotation.Value("${app.templates.external-directory}") String rawPath) {
        Path configured = Path.of(rawPath);
        if (!configured.isAbsolute()) {
            throw new IllegalStateException("app.templates.external-directory는 절대 경로여야 합니다.");
        }
        Path directory;
        try {
            directory = configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (Exception e) {
            throw new IllegalStateException("외부 template directory를 확인할 수 없습니다.", e);
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IllegalStateException("외부 template directory는 심볼릭 링크가 아닌 디렉터리여야 합니다.");
        }
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix(directory.toUri().toString());
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setOrder(0);
        return resolver;
    }
}

