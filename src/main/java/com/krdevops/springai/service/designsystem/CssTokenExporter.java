package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.TokenExportManifest;
import org.springframework.stereotype.Service;
import java.util.Comparator;

@Service
public class CssTokenExporter {
    public String export(TokenExportManifest manifest) {
        if (manifest == null || !manifest.hasValidContentHash()) throw new IllegalArgumentException("Token Manifest가 유효하지 않습니다.");
        StringBuilder css = new StringBuilder(":root {\n");
        manifest.tokens().stream().sorted(Comparator.comparing(TokenExportManifest.TokenEntry::outputVariable))
                .forEach(token -> css.append("  ").append(token.outputVariable()).append(": ").append(token.value()).append(";\n"));
        return css.append("}\n").toString();
    }
}
