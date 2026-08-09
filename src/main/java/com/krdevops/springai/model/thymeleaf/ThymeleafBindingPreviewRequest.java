package com.krdevops.springai.model.thymeleaf;

/**
 * WP6 Binding 생성 진입점 입력. 모든 소스 경로와 출력 경로는 {@code projectRootPath}에 대한
 * 상대 경로이며, 서비스가 {@code LegacySourceInventoryService}와 승인형 Project Workflow의
 * 경로 경계를 각각 적용한다.
 */
public record ThymeleafBindingPreviewRequest(
        @jakarta.validation.constraints.NotBlank String projectRootPath,
        @jakarta.validation.constraints.NotBlank String screenId,
        @jakarta.validation.constraints.NotNull LegacyScreenRole screenRole,
        @jakarta.validation.constraints.NotBlank String jspRelativePath,
        @jakarta.validation.constraints.NotBlank String controllerRelativePath,
        @jakarta.validation.constraints.NotBlank String voRelativePath,
        String voSuperclassRelativePath,
        String secondaryVoRelativePath,
        @jakarta.validation.constraints.NotBlank String outputRelativePath,
        String pageTitle,
        String layoutView,
        String screenSpecificationId,
        String registRoute
) {
    public ThymeleafBindingPreviewRequest {
        requireText(projectRootPath, "projectRootPath");
        requireText(screenId, "screenId");
        if (screenRole == null) {
            throw new IllegalArgumentException("screenRole은 필수입니다.");
        }
        requireText(jspRelativePath, "jspRelativePath");
        requireText(controllerRelativePath, "controllerRelativePath");
        requireText(voRelativePath, "voRelativePath");
        requireText(outputRelativePath, "outputRelativePath");
        voSuperclassRelativePath = blankToNull(voSuperclassRelativePath);
        secondaryVoRelativePath = blankToNull(secondaryVoRelativePath);
        pageTitle = pageTitle == null || pageTitle.isBlank() ? screenId : pageTitle;
        layoutView = layoutView == null || layoutView.isBlank() ? "layout/default" : layoutView;
        screenSpecificationId = blankToNull(screenSpecificationId);
        registRoute = blankToNull(registRoute);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "는 필수입니다.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
