package com.krdevops.springai.service.evidence;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class FixtureEvidenceSecurityScanner {
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(api[_-]?key|password|secret|token)\\s*[:=]\\s*['\"]?[^\\s,'\"]+"),
            Pattern.compile("sk-[A-Za-z0-9]{16,}"));
    public ScanResult scan(String fixtureContent) {
        if (fixtureContent == null) throw new IllegalArgumentException("fixtureContent는 필수입니다.");
        List<String> findings = SECRET_PATTERNS.stream().filter(pattern -> pattern.matcher(fixtureContent).find())
                .map(Pattern::toString).toList();
        return new ScanResult(findings);
    }
    public record ScanResult(List<String> findings) {
        public ScanResult { findings = List.copyOf(findings == null ? List.of() : findings); }
        public boolean safe() { return findings.isEmpty(); }
    }
}
