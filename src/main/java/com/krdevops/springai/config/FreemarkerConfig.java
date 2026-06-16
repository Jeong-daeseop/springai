package com.krdevops.springai.config;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;

/**
 * FreeMarker CRUD 코드 생성 전용 설정.
 * spring-boot-starter-freemarker 가 아닌 순수 freemarker 라이브러리를 사용하므로
 * MVC View resolver 자동 등록이 발생하지 않는다.
 */
@org.springframework.context.annotation.Configuration
public class FreemarkerConfig {

    @Bean
    public Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "templates/crud");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        // MyBatis #{...} 를 리터럴 텍스트로 취급.
        // FreeMarker 기본값은 #{...}를 deprecated numeric interpolation으로 파싱하므로
        // DOLLAR 모드로 고정하여 ${}만 FreeMarker 보간으로 처리한다.
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }
}
