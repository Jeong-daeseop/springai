package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-08-20 Figma Desktop/REST export 증적의 실제 Gate 경계.
 *
 * <p>연혁(오진단 2회 포함, 있는 그대로 기록):
 * <ol>
 *   <li>최초 비교(원본 Frame 306:2 vs semantic Frame 388:1060)는 설계 의도와 맞지 않는
 *       짝이었다. {@code FigmaScreenSpec과_figpack_개념_및_역할_비교분석.md} §7.2가 명시하듯
 *       semantic(`FigmaScreenSpec`/KRDS 재설계) 트랙은 원본 픽셀을 복제하지 않는 게 정상이라
 *       viewport를 맞춰도 비교가 성립하지 않는다(실측 differenceRatio=1.0, 완전 불일치).</li>
 *   <li>".figpack 트랙 안에서 Plugin이 캡처 내용을 얼마나 충실히 재현했는가"로 비교 대상을
 *       정정(캡처 자체의 {@code preview.png} vs `jsp-to-figma-plugin`이 재현한 Figma Frame).
 *       크기는 일치(1440×1200)했으나 차이율 0.76% — 처음엔 anti-aliasing으로 추정했다.</li>
 *   <li>diff 이미지를 원본 영역만 잘라 육안 대조한 결과 anti-aliasing이 아니라 <b>실제 텍스트
 *       누락</b>이었다: 데이터 테이블 "질문제목" 셀과 "조회" 버튼 라벨이 재현 Frame에서
 *       통째로 비어 있었다. 근본 원인은 `jsp-to-figma-plugin`이 아니라
 *       `jsp-design-extractor`(캡처 단계) — "질문제목"/"조회"는 실제 HTML에서
 *       {@code <input type="submit" value="...">}로 구현돼 있는데(입력 요소는 자식 텍스트
 *       노드가 없고 보이는 글자는 `value` 속성에서 나옴), {@code server.ts}의 노드 타입
 *       판정이 `tag === "button"`만 BUTTON으로 인식하고 텍스트도 `element.textContent`만
 *       사용해(input은 항상 빈 문자열) `document.json`에 텍스트 자체가 담기지 않았다.</li>
 *   <li><b>수정 및 최종 검증(2026-08-20)</b>: `input[type=submit|button|reset]`을 BUTTON으로
 *       인식하고 `.value`를 텍스트로 읽도록 수정. 수정된 Extractor로 동일 QnA 목록을
 *       재캡처(`document.json`에 두 텍스트 정상 포함 확인) → `jsp-to-figma-plugin`으로
 *       Figma Frame 재생성(`576:1327`) → 원본 `preview.png`와 재비교. 텍스트는 이제 양쪽
 *       모두 존재하고, 남은 차이율(0.79%)의 diff 이미지는 모든 텍스트에 고르게 얇은 테두리만
 *       나타난다 — 이번에는 실제로 Chromium/Figma 간 폰트 렌더링(anti-aliasing) 차이로
 *       판단된다(콘텐츠 누락이 아님).</li>
 * </ol>
 *
 * <p>즉 실제 재현 결함(텍스트 누락)은 수정·검증됐다. 남은 cross-renderer 폰트 렌더링 차이에
 * 대한 임계값 정책은 <b>{@code DEFAULT_MAX_DIFFERENCE_RATIO}(0.1%)를 그대로 유지하기로
 * 확정</b>됐다(2026-08-20). 즉 이 비교는 실제 QnA 화면에서 앞으로도 통상 FAILED로 기록되는
 * 것이 정상이며, 이는 미해결 결함이 아니라 "Chromium 렌더링과 Figma 렌더링은 픽셀 단위로
 * 동일할 수 없다"는 것을 있는 그대로 기록하는 증적 테스트다. anti-aliasing을 구분해 걸러내는
 * 별도 diff 알고리즘(pixelmatch 등)을 도입하지 않는 한 이 상태가 baseline이다.
 */
class FigmaVisualEvidenceComparisonTest {
    private final FigmaVisualComparisonService service = new FigmaVisualComparisonService();

    @Test
    void recordsPluginReproductionFidelityForTheCurrentQnaEvidence() {
        Path evidence = Path.of("docs/figma/evidence/2026-08-18-web-capture");
        var result = service.compare(
                evidence.resolve("qna-list-1440-capture-preview-2026-08-20-fixed.png"),
                evidence.resolve("figma-qna-list-plugin-reproduction-frame-576-1327-2026-08-20-fixed.png"),
                evidence.resolve("qna-list-preview-vs-plugin-diff-2026-08-20-fixed.png"));

        assertThat(result.referenceWidth()).isEqualTo(1440);
        assertThat(result.referenceHeight()).isEqualTo(1200);
        assertThat(result.candidateWidth()).isEqualTo(1440);
        assertThat(result.candidateHeight()).isEqualTo(1200);
        // jsp-design-extractor 텍스트 누락 버그는 수정됐다. 남은 0.79%는 diff 이미지 전체에
        // 고르게 퍼진 텍스트 테두리뿐이라 cross-renderer anti-aliasing이며, 0.1% 임계값을
        // 그대로 유지하기로 확정했으므로(2026-08-20) 이 비교는 계속 FAILED가 baseline이다.
        assertThat(result.status()).isEqualTo(FigmaVisualComparisonService.Status.FAILED);
        assertThat(result.differenceRatio()).isCloseTo(0.0079d, org.assertj.core.data.Offset.offset(0.002d));
    }
}
