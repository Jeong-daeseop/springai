package com.krdevops.springai.service;

import java.util.List;
import java.util.Locale;

/**
 * R6-045: 실제 API 호출 전에 설정된 모델(providerId/modelId)이 이미지 입력(Vision)을
 * 지원하는지 판정하는 알려진 모델 이름 접두사 목록.
 *
 * <p>새 Vision 모델이 출시되면 이 목록을 갱신해야 한다 — 목록에 없다고 해서 그 모델이 실제로
 * Vision을 지원하지 않는다고 100% 보장할 수는 없으므로(신규 모델 누락 가능성), false 판정은
 * "알려진 목록과 일치하지 않음"을 뜻할 뿐이다. 그럼에도 provider API를 실제로 호출해 확인하는
 * 원격 capability 조회 없이, 잘못 설정된 텍스트 전용 모델(예: {@code gpt-4o-mini}가 아닌
 * {@code gpt-3.5-turbo})을 실제 요청 전에 걸러내는 것이 이 검사의 목적이다.
 */
final class VisionModelCapability {

    private static final List<String> KNOWN_VISION_MODEL_PREFIXES = List.of(
            // OpenAI
            "gpt-4o", "gpt-4-vision", "gpt-4.1", "gpt-4.5", "gpt-5", "o1", "o3", "o4",
            // Ollama / 오픈소스 멀티모달
            "llava", "bakllava", "qwen2-vl", "qwen2.5vl", "qwen2.5-vl", "qwen3-vl", "llama3.2-vision",
            "minicpm-v", "moondream", "pixtral");

    private VisionModelCapability() {
    }

    static boolean supports(String providerId, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        String normalized = modelId.toLowerCase(Locale.ROOT);
        return KNOWN_VISION_MODEL_PREFIXES.stream().anyMatch(normalized::startsWith);
    }
}
