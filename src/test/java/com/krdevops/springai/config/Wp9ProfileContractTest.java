package com.krdevops.springai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Wp9ProfileContractTest {

    @Test
    void prod는_classpath_resource와_필수_credential만_사용한다() throws Exception {
        var source = new YamlPropertySourceLoader().load("prod",
                new ClassPathResource("application-prod.yaml")).get(0);

        assertThat(source.getProperty("spring.thymeleaf.prefix")).isEqualTo("classpath:/templates/");
        assertThat(source.getProperty("spring.web.resources.static-locations"))
                .isEqualTo("classpath:/static/");
        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(source.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(source.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(source.getProperty("spring.ai.vectorstore.redis.uri")).isEqualTo("${REDIS_URI}");
    }

    @Test
    void 개발용_credential_fallback은_dev_profile에만_존재한다() throws Exception {
        String prod = new ClassPathResource("application-prod.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        String dev = new ClassPathResource("application-dev.yaml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(prod).doesNotContain("DB_USERNAME:", "DB_PASSWORD:", "REDIS_URI:");
        assertThat(dev).contains("${DB_USERNAME:ebt}", "${DB_PASSWORD:ebt01}");
    }
}

