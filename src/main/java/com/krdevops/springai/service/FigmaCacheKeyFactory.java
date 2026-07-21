package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.model.design.FigmaReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FigmaCacheKeyFactory {

    private final DesignVisionProperties properties;

    public String create(FigmaReference reference, String fileVersion, String featureType) {
        String canonical = "sourceType=FIGMA\n"
                + "fileKey=" + reference.fileKey() + "\n"
                + "nodeId=" + reference.nodeId() + "\n"
                + "fileVersion=" + required(fileVersion, "fileVersion") + "\n"
                + "featureType=" + normalizeFeatureType(featureType) + "\n"
                + "mapperVersion=" + properties.getFigma().getMapperVersion() + "\n"
                + "depth=" + properties.getFigma().getDepthLimit() + "\n"
                + "geometry=none";
        return sha256(canonical);
    }

    public String normalizeFeatureType(String featureType) {
        return featureType == null || featureType.isBlank()
                ? "crud" : featureType.trim().toLowerCase(Locale.ROOT);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "가 필요합니다.");
        return value;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Figma 캐시 키 생성 실패", e);
        }
    }
}
