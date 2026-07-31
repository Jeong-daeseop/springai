package com.krdevops.springai.service.figma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * R6-012: DEC-10=REST 경로에서 Figma Plugin이 장기 `X-API-Key` 대신 쓸 수 있는 단기 토큰.
 * 서버 상태를 두지 않는 self-contained HMAC 토큰이라 재시작해도 기존 발급 토큰이 그대로
 * 유효하며, 별도 저장소·만료 정리 로직이 필요 없다.
 */
@Service
public class FigmaRestTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String secret;
    private final long ttlSeconds;

    public FigmaRestTokenService(
            @Value("${app.figma.rest-token-secret:}") String secret,
            @Value("${app.figma.rest-token-ttl-seconds:900}") long ttlSeconds
    ) {
        this.secret = secret == null ? "" : secret;
        this.ttlSeconds = ttlSeconds;
    }

    public boolean isEnabled() {
        return !secret.isBlank();
    }

    public IssuedToken issue() {
        if (!isEnabled()) {
            throw new IllegalStateException("app.figma.rest-token-secret이 설정되지 않아 단기 토큰을 발급할 수 없습니다.");
        }
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        String payload = String.valueOf(expiresAt.toEpochMilli());
        String signature = sign(payload);
        String token = encode(payload) + "." + encode(signature);
        return new IssuedToken(token, expiresAt);
    }

    public boolean verify(String token) {
        if (!isEnabled() || token == null) return false;
        int separator = token.indexOf('.');
        if (separator < 0) return false;
        String payload = decode(token.substring(0, separator));
        String providedSignature = decode(token.substring(separator + 1));
        if (payload == null || providedSignature == null) return false;
        String expectedSignature = sign(payload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
            return false;
        }
        try {
            long expiresAtMillis = Long.parseLong(payload);
            return Instant.now().isBefore(Instant.ofEpochMilli(expiresAtMillis));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC 서명에 실패했습니다.", e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {}
}
