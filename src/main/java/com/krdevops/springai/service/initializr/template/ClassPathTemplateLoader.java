package com.krdevops.springai.service.initializr.template;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * classpath:templates/egov/{name}.tpl 파일을 로드하여 ${key} 치환을 수행합니다.
 * Map에 없는 키(예: Spring EL ${DB_URL}, Logback ${LOG_PATH})는 원본 그대로 유지됩니다.
 */
@Component
public class ClassPathTemplateLoader {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * @param name   파일명 (확장자 포함, 예: "context-common.xml.tpl")
     * @param vars   치환할 키-값 쌍
     * @return 치환된 문자열
     */
    public String load(String name, Map<String, String> vars) {
        String template = readTemplate(name);
        return substitute(template, vars);
    }

    /** vars 없이 그대로 반환 */
    public String load(String name) {
        return readTemplate(name);
    }

    private String readTemplate(String name) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/egov/" + name);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("템플릿 파일을 읽을 수 없습니다: templates/egov/" + name, e);
        }
    }

    private String substitute(String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key   = m.group(1);
            String value = vars.get(key);
            m.appendReplacement(sb, value != null
                    ? Matcher.quoteReplacement(value)
                    : Matcher.quoteReplacement(m.group(0))); // 미매칭 키는 원본 유지
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
