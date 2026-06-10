package com.krdevops.springai.service.menu;

import com.krdevops.springai.model.MenuRegistrationSpec;
import org.springframework.stereotype.Component;

@Component
public class MenuInputValidator {

    public void validateMenuNo(String menuNo) {
        if (menuNo == null || menuNo.isBlank()) {
            throw new IllegalArgumentException("menuNo는 필수 입력값입니다.");
        }
        try {
            Integer.parseInt(menuNo.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("menuNo는 숫자여야 합니다: " + menuNo);
        }
    }

    public MenuRegistrationSpec validateAndBuild(String upperMenuNo, String urlPrefix,
                                                  String menuNm, String progrmFileNm) {
        if (upperMenuNo == null || upperMenuNo.isBlank()) {
            throw new IllegalArgumentException("upperMenuNo는 필수 입력값입니다.");
        }
        int upperMenuNoInt;
        try {
            upperMenuNoInt = Integer.parseInt(upperMenuNo.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("upperMenuNo는 숫자여야 합니다: " + upperMenuNo);
        }

        if (urlPrefix == null || urlPrefix.isBlank()) {
            throw new IllegalArgumentException("urlPrefix는 필수 입력값입니다.");
        }
        String normalizedPrefix = normalizeUrlPrefix(urlPrefix.trim());

        if (menuNm == null || menuNm.isBlank()) {
            throw new IllegalArgumentException("menuNm은 필수 입력값입니다.");
        }

        if (progrmFileNm == null || progrmFileNm.isBlank()) {
            throw new IllegalArgumentException("progrmFileNm은 필수 입력값입니다.");
        }

        return new MenuRegistrationSpec(upperMenuNoInt, normalizedPrefix,
                menuNm.trim(), progrmFileNm.trim());
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
