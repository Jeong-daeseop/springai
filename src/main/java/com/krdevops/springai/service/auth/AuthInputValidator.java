package com.krdevops.springai.service.auth;

import com.krdevops.springai.model.AuthRegistrationSpec;
import org.springframework.stereotype.Component;

@Component
public class AuthInputValidator {

    public AuthRegistrationSpec validateAndBuild(String urlPrefix, String programNm, String domain) {
        if (urlPrefix == null || urlPrefix.isBlank()) {
            throw new IllegalArgumentException("urlPrefix는 필수 입력값입니다.");
        }
        String normalizedPrefix = normalizeUrlPrefix(urlPrefix.trim());

        if (programNm == null || programNm.isBlank()) {
            throw new IllegalArgumentException("programNm은 필수 입력값입니다.");
        }

        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain은 필수 입력값입니다.");
        }

        return new AuthRegistrationSpec(normalizedPrefix, programNm.trim(), domain.trim());
    }

    private String normalizeUrlPrefix(String prefix) {
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.length() > 1 && prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }
}
