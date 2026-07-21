package com.krdevops.springai.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class WebCaptureCacheKeyFactory {
    public String create(String contentHash, String featureType, String schemaVersion, String mapperVersion) {
        try {
            String canonical = "sourceType=WEB_CAPTURE\ncontentHash=" + contentHash
                    + "\nfeatureType=" + featureType + "\nschemaVersion=" + schemaVersion
                    + "\nmapperVersion=" + mapperVersion;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("WEB_CAPTURE cache key 생성 실패", e);
        }
    }
}
