package com.krdevops.springai.model.artifact;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** SHA-256 hex content hash 검증·계산 유틸. contentHash 형식이 곧 경로 traversal 방어선이다. */
public final class ContentHashes {

    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    private ContentHashes() {
    }

    public static boolean isValid(String value) {
        return value != null && SHA256_HEX.matcher(value).matches();
    }

    public static String requireValid(String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("contentHash는 64자 소문자 SHA-256 hex여야 합니다.");
        }
        return value;
    }

    public static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
