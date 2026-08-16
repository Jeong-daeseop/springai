package com.krdevops.springai.service.figma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

/**
 * R6-012/MR-DEC-05: DEC-10=REST 경로에서 Figma Plugin이 장기 `X-API-Key` 대신 쓸 수 있는 단기 토큰.
 * 서버 상태를 두지 않는 self-contained HMAC 토큰이라 재시작해도 기존 발급 토큰이 그대로
 * 유효하며, 별도 저장소·만료 정리 로직이 필요 없다.
 *
 * <p>MR-A06: payload에 Scope 목록을 포함해 Plugin Token이 승인·반려 같은 운영자 전용 동작을
 * 수행하지 못하게 경계를 긋는다. Scope 구분자(`|`)가 없는 기존 발급 토큰은 레거시로 간주해
 * {@link #SCOPE_SCREENS_READ} 하나만 가진 것으로 취급한다(하위 호환).</p>
 */
@Service
public class FigmaRestTokenService {

    public static final String SCOPE_SCREENS_READ = "figma:screens:read";
    public static final String SCOPE_REFINEMENTS_WRITE = "figma:refinements:write";
    public static final String SCOPE_REPORTS_WRITE = "figma:reports:write";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Set<String> LEGACY_SCOPES = Set.of(SCOPE_SCREENS_READ);

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

    /** 레거시 호환: 조회 전용(Scope=screens:read) 토큰을 발급한다. */
    public IssuedToken issue() {
        return issue(Set.of(SCOPE_SCREENS_READ));
    }

    public IssuedToken issue(Set<String> scopes) {
        if (!isEnabled()) {
            throw new IllegalStateException("app.figma.rest-token-secret이 설정되지 않아 단기 토큰을 발급할 수 없습니다.");
        }
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("최소 1개 이상의 Scope가 필요합니다.");
        }
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        String payload = expiresAt.toEpochMilli() + "|" + String.join(",", scopes);
        String signature = sign(payload);
        String token = encode(payload) + "." + encode(signature);
        return new IssuedToken(token, expiresAt, Set.copyOf(scopes));
    }

    /** 레거시 호환: Scope 검증 없이 유효성만 확인한다. */
    public boolean verify(String token) {
        return verifyWithScopes(token).valid();
    }

    public VerificationResult verifyWithScopes(String token) {
        if (!isEnabled() || token == null) return VerificationResult.INVALID;
        int separator = token.indexOf('.');
        if (separator < 0) return VerificationResult.INVALID;
        String payload = decode(token.substring(0, separator));
        String providedSignature = decode(token.substring(separator + 1));
        if (payload == null || providedSignature == null) return VerificationResult.INVALID;
        String expectedSignature = sign(payload);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8))) {
            return VerificationResult.INVALID;
        }
        int scopeSeparator = payload.indexOf('|');
        String expiresPart = scopeSeparator < 0 ? payload : payload.substring(0, scopeSeparator);
        Set<String> scopes = scopeSeparator < 0
                ? LEGACY_SCOPES
                : Set.copyOf(Arrays.asList(payload.substring(scopeSeparator + 1).split(",")));
        try {
            long expiresAtMillis = Long.parseLong(expiresPart);
            if (!Instant.now().isBefore(Instant.ofEpochMilli(expiresAtMillis))) {
                return VerificationResult.INVALID;
            }
            return new VerificationResult(true, scopes);
        } catch (NumberFormatException e) {
            return VerificationResult.INVALID;
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

    public record IssuedToken(String token, Instant expiresAt, Set<String> scopes) {}

    public record VerificationResult(boolean valid, Set<String> scopes) {
        public static final VerificationResult INVALID = new VerificationResult(false, Set.of());

        public boolean hasScope(String scope) {
            return valid && scopes.contains(scope);
        }
    }
}
