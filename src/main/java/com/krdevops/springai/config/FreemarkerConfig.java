package com.krdevops.springai.config;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * FreeMarker CRUD 코드 생성 전용 설정.
 * spring-boot-starter-freemarker 가 아닌 순수 freemarker 라이브러리를 사용하므로
 * MVC View resolver 자동 등록이 발생하지 않는다.
 */
@org.springframework.context.annotation.Configuration
public class FreemarkerConfig {

    @Bean
    @Primary
    public Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "templates/crud");
        applyCommonSettings(cfg);
        return cfg;
    }

    /**
     * 게시판(BBS) 전용 FreeMarker 설정.
     * 템플릿 base 를 {@code templates} 로 두어 {@code board/vo.java.ftl} 형식으로 참조한다.
     * CRUD 설정과 base 경로가 다르므로 별도 빈으로 분리한다.
     */
    @Bean
    public Configuration boardFreemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "templates");
        applyCommonSettings(cfg);
        return cfg;
    }

    private void applyCommonSettings(Configuration cfg) {
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        // MyBatis #{...} 를 리터럴 텍스트로 취급.
        // FreeMarker 기본값은 #{...}를 deprecated numeric interpolation으로 파싱하므로
        // DOLLAR 모드로 고정하여 ${}만 FreeMarker 보간으로 처리한다.
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
    }
}
