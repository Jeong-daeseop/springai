package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ScreenDecisionContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * I-7B: Screen Decision 결과 캐시.
 * 동일한 JSP 구조에 대한 반복 분석을 피합니다.
 */
@Service
public class ScreenDecisionCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 3600000; // 1 hour

    public record CacheEntry(
        ScreenDecisionContext decision,
        long createdAt) {

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    /**
     * 캐시에서 decision을 조회합니다.
     */
    public ScreenDecisionContext get(String htmlFingerprint) {
        CacheEntry entry = cache.get(htmlFingerprint);

        if (entry == null) {
            return null;
        }

        if (entry.isExpired()) {
            cache.remove(htmlFingerprint);
            return null;
        }

        return entry.decision();
    }

    /**
     * Decision을 캐시에 저장합니다.
     */
    public void put(String htmlFingerprint, ScreenDecisionContext decision) {
        cache.put(htmlFingerprint, new CacheEntry(decision, System.currentTimeMillis()));
    }

    /**
     * HTML 콘텐츠로부터 fingerprint를 생성합니다.
     */
    public String computeFingerprint(String htmlContent) {
        return String.valueOf(htmlContent.hashCode());
    }

    /**
     * 캐시 통계를 조회합니다.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEntries", cache.size());
        stats.put("validEntries", cache.values().stream()
            .filter(e -> !e.isExpired())
            .count());
        stats.put("expiredEntries", cache.values().stream()
            .filter(CacheEntry::isExpired)
            .count());
        stats.put("cacheTtlMs", CACHE_TTL_MS);

        return stats;
    }

    /**
     * 만료된 캐시 항목을 정리합니다.
     */
    public void cleanupExpired() {
        cache.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * 전체 캐시를 초기화합니다.
     */
    public void clear() {
        cache.clear();
    }
}
