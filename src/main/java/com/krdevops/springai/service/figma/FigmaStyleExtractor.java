package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Figma Styles API 응답에서 Design Token을 추출.
 * 2-A4 FigmaStyleExtractor 구현
 *
 * 색상, 타이포그래피, 간격, radius 등을 Token 후보로 제공
 * (Profile 변경이 아닌 토큰 후보만 반환)
 */
@Service
public class FigmaStyleExtractor {

    /**
     * Styles 응답에서 색상, 타이포, 간격 토큰을 추출.
     */
    public DesignTokenExtraction extractTokens(FigmaStylesResponse stylesResponse) {
        if (stylesResponse == null || stylesResponse.meta() == null) {
            return new DesignTokenExtraction(
                    List.of(), List.of(), List.of(), List.of()
            );
        }

        List<ColorToken> colors = new ArrayList<>();
        List<TypographyToken> typographies = new ArrayList<>();
        List<SpacingToken> spacings = new ArrayList<>();
        List<RadiusToken> radii = new ArrayList<>();

        for (var style : stylesResponse.meta().styles()) {
            try {
                switch (style.styleType().toUpperCase()) {
                    case "FILL" -> colors.add(parseColorToken(style));
                    case "TEXT" -> typographies.add(parseTypographyToken(style));
                    case "STROKE" -> colors.add(parseColorToken(style));
                    case "EFFECT" -> radii.add(parseRadiusToken(style));
                    case "GRID" -> spacings.add(parseSpacingToken(style));
                }
            } catch (Exception e) {
                // 개별 토큰 파싱 실패는 무시
            }
        }

        return new DesignTokenExtraction(colors, typographies, spacings, radii);
    }

    private ColorToken parseColorToken(FigmaStylesResponse.StyleRef style) {
        return new ColorToken(
                style.name(),
                style.key(),
                style.description(),
                normalizeColorName(style.name())
        );
    }

    private TypographyToken parseTypographyToken(FigmaStylesResponse.StyleRef style) {
        return new TypographyToken(
                style.name(),
                style.key(),
                style.description(),
                extractFontSize(style.name()),
                extractFontWeight(style.name())
        );
    }

    private SpacingToken parseSpacingToken(FigmaStylesResponse.StyleRef style) {
        return new SpacingToken(
                style.name(),
                style.key(),
                style.description(),
                extractSpacingValue(style.name())
        );
    }

    private RadiusToken parseRadiusToken(FigmaStylesResponse.StyleRef style) {
        return new RadiusToken(
                style.name(),
                style.key(),
                style.description(),
                extractRadiusValue(style.name())
        );
    }

    private String normalizeColorName(String name) {
        return name.toLowerCase().replace(" ", "-").replace("/", "_");
    }

    private String extractFontSize(String name) {
        if (name.contains("12")) return "12px";
        if (name.contains("14")) return "14px";
        if (name.contains("16")) return "16px";
        if (name.contains("18")) return "18px";
        if (name.contains("20")) return "20px";
        return "16px";
    }

    private String extractFontWeight(String name) {
        if (name.toLowerCase().contains("bold")) return "700";
        if (name.toLowerCase().contains("semi")) return "600";
        if (name.toLowerCase().contains("medium")) return "500";
        return "400";
    }

    private String extractSpacingValue(String name) {
        if (name.contains("4")) return "4px";
        if (name.contains("8")) return "8px";
        if (name.contains("12")) return "12px";
        if (name.contains("16")) return "16px";
        if (name.contains("24")) return "24px";
        if (name.contains("32")) return "32px";
        return "16px";
    }

    private String extractRadiusValue(String name) {
        if (name.contains("4")) return "4px";
        if (name.contains("8")) return "8px";
        if (name.contains("12")) return "12px";
        return "8px";
    }

    public record DesignTokenExtraction(
            List<ColorToken> colors,
            List<TypographyToken> typographies,
            List<SpacingToken> spacings,
            List<RadiusToken> radii
    ) {
        public int totalCount() {
            return colors.size() + typographies.size() + spacings.size() + radii.size();
        }
    }

    public record ColorToken(
            String name,
            String figmaKey,
            String description,
            String normalizedName
    ) {}

    public record TypographyToken(
            String name,
            String figmaKey,
            String description,
            String fontSize,
            String fontWeight
    ) {}

    public record SpacingToken(
            String name,
            String figmaKey,
            String description,
            String value
    ) {}

    public record RadiusToken(
            String name,
            String figmaKey,
            String description,
            String value
    ) {}
}
