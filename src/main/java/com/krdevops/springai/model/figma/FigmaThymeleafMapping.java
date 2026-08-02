package com.krdevops.springai.model.figma;

import java.util.List;
import java.util.Map;

/**
 * I-6A: Figma ↔ Thymeleaf 매핑.
 */
public record FigmaThymeleafMapping(
    String figmaFileId,
    String figmaNodeId,
    String screenName,
    String thymeleafTemplatePath,
    Map<String, String> componentBindings,
    List<String> designTokens,
    long timestamp) {

    public FigmaThymeleafMapping {
        if (componentBindings == null) {
            componentBindings = Map.of();
        }
        if (designTokens == null) {
            designTokens = List.of();
        }
    }

    public boolean isValid() {
        return figmaFileId != null && !figmaFileId.isEmpty() &&
               thymeleafTemplatePath != null && !thymeleafTemplatePath.isEmpty();
    }
}
