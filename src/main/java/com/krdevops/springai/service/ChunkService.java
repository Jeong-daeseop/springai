package com.krdevops.springai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 대용량 문서를 일정 크기로 분할하는 청킹 서비스.
 *
 * [청킹 전략]
 * - 기본 700자 단위 분할 (ONNX tokenizer maxLength=512 토큰 기준 안전 마진 확보)
 * - 100자 overlap으로 청크 경계 문맥 유지
 * - 자연 경계 우선 분할: 줄바꿈 → 문장부호(. ! ? 。) → 공백 순으로 탐색
 *   → 단어/문장 중간 절단 최소화
 *
 * [적용 대상]
 * - RagService.ingestText()      : 텍스트·Markdown 문서
 * - RagService.ingestJavaDirectory() : 소스코드 파일
 * - RagService.ingestUrl()       : URL 크롤링 텍스트
 */
@Slf4j
@Service
public class ChunkService {

    /** 청크당 최대 문자 수 (ONNX maxLength=512 토큰 기준 700자 안전 마진) */
    public static final int DEFAULT_CHUNK_SIZE    = 700;

    /** 청크 간 겹침 문자 수 (문맥 연속성 유지) */
    public static final int DEFAULT_CHUNK_OVERLAP = 100;

    /** 자연 경계 탐색 시 후방 탐색 최대 거리 */
    private static final int BOUNDARY_LOOKBACK = 120;

    /**
     * 기본 설정(700자 / 100자 overlap)으로 텍스트를 청크로 분할한다.
     *
     * @param text 분할할 텍스트
     * @return 청크 목록 (빈 문자열 제외)
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    /**
     * 지정 크기로 텍스트를 청크로 분할한다.
     *
     * @param text      분할할 텍스트
     * @param chunkSize 청크당 최대 문자 수
     * @param overlap   청크 간 겹침 문자 수
     * @return 청크 목록
     */
    public List<String> chunk(String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String normalized = text.strip();
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < normalized.length()) {
            int end = Math.min(start + chunkSize, normalized.length());

            // 마지막 청크가 아니면 자연 경계로 조정
            if (end < normalized.length()) {
                end = findNaturalBoundary(normalized, end);
            }

            String chunk = normalized.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= normalized.length()) break;

            // 다음 시작점: overlap 만큼 되돌아감
            start = Math.max(start + 1, end - overlap);
        }

        log.debug("청킹 완료: 원문 {}자 → {}개 청크 (chunkSize={}, overlap={})",
            normalized.length(), chunks.size(), chunkSize, overlap);

        return chunks;
    }

    /**
     * 주어진 위치에서 후방으로 탐색하여 자연스러운 분할 경계를 찾는다.
     *
     * 우선순위: 줄바꿈(\n) → 문장부호(. ! ? 。) → 공백
     *
     * @param text 전체 텍스트
     * @param pos  기준 위치
     * @return 조정된 분할 위치
     */
    private int findNaturalBoundary(String text, int pos) {
        int lookbackLimit = Math.max(0, pos - BOUNDARY_LOOKBACK);

        // 1순위: 줄바꿈 — Markdown 문서의 단락 경계
        for (int i = pos; i >= lookbackLimit; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }

        // 2순위: 문장부호 뒤 공백 — 문장 경계
        for (int i = pos; i >= lookbackLimit; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '。')
                    && i + 1 < text.length()
                    && text.charAt(i + 1) == ' ') {
                return i + 2;
            }
        }

        // 3순위: 공백 — 단어 경계
        for (int i = pos; i >= lookbackLimit; i--) {
            if (text.charAt(i) == ' ') {
                return i + 1;
            }
        }

        // 경계 없음 — 원래 위치 사용
        return pos;
    }
}
