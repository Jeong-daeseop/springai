package com.krdevops.springai.service.security;
import org.springframework.stereotype.Service;
import java.time.Duration; import java.time.Instant;
@Service public class ShortLivedDownloadPolicy { public Instant expiresAt(Duration ttl){if(ttl==null||ttl.isNegative()||ttl.isZero())throw new IllegalArgumentException("Download TTL은 양수여야 합니다."); return Instant.now().plus(ttl);} public void requireActive(Instant expiresAt){if(expiresAt==null||!expiresAt.isAfter(Instant.now()))throw new IllegalStateException("Download URL이 만료되었습니다.");} }
