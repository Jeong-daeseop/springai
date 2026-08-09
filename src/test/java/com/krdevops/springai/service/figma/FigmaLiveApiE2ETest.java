package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.config.OperationalResilienceProperties;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.service.FigmaApiClient;
import com.krdevops.springai.service.FigmaApiException;
import com.krdevops.springai.service.FigmaReferenceValidator;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ARCH-0817/ARCH-0822: 실제 Figma REST API에 붙어 URL 파싱 → 노드 조회 계약을 검증한다.
 *
 * <p>대상 파일과 토큰은 전부 환경변수에서 읽는다(소스에 하드코딩하지 않는다).
 * {@code FIGMA_ACCESS_TOKEN}/{@code FIGMA_RELEASE_A_URL}/{@code FIGMA_RELEASE_A_NODE_ID} 중
 * 하나라도 없으면 전체가 스킵되므로 CI·오프라인 환경의 회귀 스위트에는 영향을 주지 않는다.
 * 실행하려면 {@code set -a; source .env; set +a; ./gradlew test --tests "*FigmaLiveApiE2ETest*"}.
 *
 * <p>GET만 사용하며 대상(KRDS 공유 디자인 시스템) 파일 내용은 절대 변경하지 않는다.
 * 토큰·fileKey·nodeId 값이 실패 메시지로 새지 않도록 단정문에는 원본 값을 넣지 않는다.
 */
class FigmaLiveApiE2ETest {

    /** 실제 파일에는 존재할 수 없는 노드 ID — 삭제된 editable 노드를 흉내낸다. */
    private static final String NON_EXISTENT_NODE_ID = "0:99999999";

    private FigmaReference reference;
    private FigmaApiClient client;

    @BeforeEach
    void requireLiveFigmaCredentials() {
        String accessToken = System.getenv("FIGMA_ACCESS_TOKEN");
        String figmaUrl = System.getenv("FIGMA_RELEASE_A_URL");
        String nodeId = System.getenv("FIGMA_RELEASE_A_NODE_ID");
        Assumptions.assumeTrue(isPresent(accessToken),
                "FIGMA_ACCESS_TOKEN이 없어 실네트워크 Figma 테스트를 건너뜁니다.");
        Assumptions.assumeTrue(isPresent(figmaUrl) && isPresent(nodeId),
                "FIGMA_RELEASE_A_URL/FIGMA_RELEASE_A_NODE_ID가 없어 실네트워크 Figma 테스트를 건너뜁니다.");

        DesignVisionProperties properties = new DesignVisionProperties();
        properties.getFigma().setEnabled(true);
        properties.getFigma().setAccessToken(accessToken.trim());

        reference = new FigmaReferenceValidator(properties).validate(figmaUrl.trim(), nodeId.trim());
        client = new FigmaApiClient(properties, new ObjectMapper(),
                new ExternalCallGuard(new OperationalResilienceProperties()));
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /** ARCH-0817: 실제 Figma URL의 노드를 조회하면 파일 버전과 노드 문서가 함께 돌아온다. */
    @Test
    void fetchesRealNodeWithFileVersionAndDocument() {
        FigmaNodeDocument document = client.fetchNode(reference);

        assertThat(document.fileVersion()).as("Figma 파일 버전").isNotBlank();
        assertThat(document.document().isObject()).as("노드 문서가 객체인지").isTrue();
        assertThat(document.document().hasNonNull("type")).as("노드 type 필드 존재 여부").isTrue();
    }

    /**
     * ARCH-0822: 존재하지 않는 노드는 실제 Figma에서 404가 아니라 200 + {@code nodes:{id:null}}로
     * 돌아온다. editable scope 충돌 판정이 이 응답을 놓치지 않는지 실응답으로 확인한다.
     */
    @Test
    void missingNodeIsReportedAsNodeNotFound() {
        FigmaReference missing = new FigmaReference(reference.fileKey(), NON_EXISTENT_NODE_ID);

        assertThatThrownBy(() -> client.fetchNode(missing))
                .isInstanceOfSatisfying(FigmaApiException.class, error ->
                        assertThat(error.code()).as("삭제된 노드 오류 코드")
                                .isEqualTo("FIGMA_NODE_NOT_FOUND"));
    }

    /**
     * ARCH-0822: Apply 직전 scope 재검증이 실제로 쓰는 배치 조회 경로. 실존 노드와 없는 노드를
     * 섞어 한 번에 물어보고 없는 것만 정확히 골라내는지 실응답으로 확인한다.
     */
    @Test
    void findMissingNodeIdsSeparatesLiveNodeFromDeletedNode() {
        List<String> missing = client.findMissingNodeIds(
                reference.fileKey(), List.of(reference.nodeId(), NON_EXISTENT_NODE_ID));

        assertThat(missing).as("실제 Figma가 없다고 답한 노드")
                .containsExactly(NON_EXISTENT_NODE_ID);
    }
}
