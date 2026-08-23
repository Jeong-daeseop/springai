package com.krdevops.springai.service.security;
import org.springframework.stereotype.Service;
import java.time.Duration;
@Service public class ArtifactRetentionPolicy { public InstantPair retention(Duration retention){if(retention==null||retention.isNegative()||retention.isZero())throw new IllegalArgumentException("보존 기간은 양수여야 합니다."); java.time.Instant now=java.time.Instant.now();return new InstantPair(now,now.plus(retention));} public record InstantPair(java.time.Instant createdAt,java.time.Instant expiresAt){} }
