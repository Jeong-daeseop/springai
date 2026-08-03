package com.krdevops.springai.config.mcp;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

/** ARCH-0104/0105: 현재·회전 이전 token을 상수시간으로 검증한다. */
@Component
public class McpCredentialValidator {

    public enum Status { VALID_CURRENT, VALID_PREVIOUS, MISSING, INVALID, EXPIRED_PREVIOUS, NOT_CONFIGURED }

    public record Result(Status status) {
        public boolean authenticated() {
            return status == Status.VALID_CURRENT || status == Status.VALID_PREVIOUS;
        }

        public String credentialVersion() {
            return status == Status.VALID_PREVIOUS ? "previous" : status == Status.VALID_CURRENT ? "current" : null;
        }
    }

    private final McpSecurityProperties properties;
    private final Clock clock;

    @Autowired
    public McpCredentialValidator(McpSecurityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    McpCredentialValidator(McpSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Result validate(String provided) {
        if (!properties.hasSharedToken() && !properties.hasPreviousSharedToken()) {
            return new Result(Status.NOT_CONFIGURED);
        }
        if (provided == null || provided.isBlank()) {
            return new Result(Status.MISSING);
        }
        if (constantTimeEquals(properties.getSharedToken(), provided)) {
            return new Result(Status.VALID_CURRENT);
        }
        if (properties.hasPreviousSharedToken() && constantTimeEquals(properties.getPreviousSharedToken(), provided)) {
            Instant validUntil = properties.getPreviousTokenValidUntil();
            if (validUntil != null && clock.instant().isBefore(validUntil)) {
                return new Result(Status.VALID_PREVIOUS);
            }
            return new Result(Status.EXPIRED_PREVIOUS);
        }
        return new Result(Status.INVALID);
    }

    static boolean constantTimeEquals(String expected, String provided) {
        byte[] expectedBytes = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
    }
}
