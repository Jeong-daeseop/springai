package com.krdevops.springai.service.generation.layout;

import com.krdevops.springai.model.crud.CrudLayerDefinition;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout 5종/GNB 컴포넌트 4종/main.html의 저장 경로와 보존(skip) 여부를 계산한다.
 * 파일 렌더링·저장은 하지 않는다({@link ThymeleafLayoutGenerationService}가 수행).
 */
@Component
public class ThymeleafLayoutGenerationPlanner {

    public List<PlannedFile> planLayoutFiles(Path outputPath, String resolvedBasePath, boolean overwrite) {
        List<PlannedFile> planned = new ArrayList<>();
        for (CrudLayerDefinition layer : CrudLayerDefinition.thymeleafLayoutLayers()) {
            String fileName = switch (layer.layerKey()) {
                case CrudLayerDefinition.LAYOUT_HTML -> "default.html";
                case CrudLayerDefinition.LAYOUT_GNB_HTML -> "gnb.html";
                case CrudLayerDefinition.LAYOUT_LNB_HTML -> "lnb.html";
                case CrudLayerDefinition.LAYOUT_BREADCRUMB_HTML -> "breadcrumb.html";
                case CrudLayerDefinition.LAYOUT_FOOTER_HTML -> "footer.html";
                default -> throw new IllegalArgumentException("layout layer가 아닙니다: " + layer.layerKey());
            };
            Path filePath = Paths.get(outputPath.toString(), "src/main/resources/templates", resolvedBasePath, fileName)
                    .normalize();
            boolean skip = !overwrite && Files.exists(filePath);
            planned.add(new PlannedFile(layer.layerKey(), filePath, skip));
        }
        return planned;
    }

    public List<PlannedFile> planGnbComponents(Path outputPath, String resolvedPackageName, boolean overwrite) {
        List<PlannedFile> planned = new ArrayList<>();
        String pkgSub = resolvedPackageName.replace(".", "/");
        for (CrudLayerDefinition layer : CrudLayerDefinition.GNB_MENU_COMPONENT_LAYERS) {
            String relativePath = layer.resolveSubPath(pkgSub, "") + layer.fileNameSuffix();
            Path filePath = Paths.get(outputPath.toString(), relativePath).normalize();
            boolean skip = !overwrite && Files.exists(filePath);
            planned.add(new PlannedFile(layer.layerKey(), filePath, skip));
        }
        return planned;
    }

    public PlannedFile planMainHtml(Path outputPath, boolean overwrite) {
        Path mainHtmlPath = Paths.get(outputPath.toString(),
                "src/main/resources/templates/egovframework/main/main.html").normalize();
        boolean skip = !overwrite && Files.exists(mainHtmlPath);
        return new PlannedFile(null, mainHtmlPath, skip);
    }

    public record PlannedFile(String layerKey, Path path, boolean skip) {
    }
}
