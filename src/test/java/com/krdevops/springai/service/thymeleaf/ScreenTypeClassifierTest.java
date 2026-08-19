package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.krdevops.springai.config.StubEmbeddingModelTestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI 러너에는 로컬 전용 ko-sroberta ONNX 모델 파일도, Redis 서버도 없어 이 테스트가 필요로 하지
 * 않는 실제 임베딩/VectorStore auto-config가 각각 파일 I/O와 연결 단계에서 실패한다.
 * spring.ai.model.embedding=none + spring.ai.vectorstore.redis.enabled=false(TestPropertySource)로
 * 끄고 StubEmbeddingModelTestConfig의 no-op Bean으로 대체한다 — 전자만으로는 부족하다.
 * TransformersEmbeddingModelAutoConfiguration의 @ConditionalOnMissingBean이 구현 클래스
 * (TransformersEmbeddingModel) 기준이라 인터페이스 스텁 Bean만으로는 비활성화되지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.ai.model.embedding=none", "spring.ai.vectorstore.redis.enabled=false",
        "spring.ai.openai.api-key=test-key-not-used"})
@Import(StubEmbeddingModelTestConfig.class)
@DisplayName("I-4A: ScreenTypeClassifier 테스트")
class ScreenTypeClassifierTest {

    @Autowired
    private ScreenTypeClassifier classifier;

    @Test
    @DisplayName("NULL 입력 처리")
    void testNullInput() {
        var decision = classifier.determineArchetype(null);
        assertEquals("FORM", decision.archetype());
        assertTrue(decision.confidence() >= 0.5);
    }

    @Test
    @DisplayName("빈 pageList 처리")
    void testEmptyPages() {
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "테스트",
            "CRUD", null, "db1", "customers",
            List.of(), List.of(), List.of(),
            LocalDateTime.now()
        );
        var decision = classifier.determineArchetype(spec);
        assertEquals("FORM", decision.archetype());
    }

    @Test
    @DisplayName("template이 LIST인 경우")
    void testExplicitListArchetype() {
        var pageSpec = new PageSpec(
            "page1", "LIST", List.of(), List.of()
        );
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "고객 목록",
            "CRUD", null, "db1", "customers",
            List.of(), List.of(pageSpec), List.of(),
            LocalDateTime.now()
        );

        var decision = classifier.determineArchetype(spec);
        assertEquals("LIST", decision.archetype());
        assertEquals(0.95, decision.confidence(), 0.01);
    }

    @Test
    @DisplayName("template이 FORM인 경우")
    void testExplicitFormArchetype() {
        var pageSpec = new PageSpec(
            "page1", "FORM", List.of(), List.of()
        );
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "등록 폼",
            "CRUD", null, "db1", "customers",
            List.of(), List.of(pageSpec), List.of(),
            LocalDateTime.now()
        );

        var decision = classifier.determineArchetype(spec);
        assertEquals("FORM", decision.archetype());
        assertEquals(0.95, decision.confidence(), 0.01);
    }

    @Test
    @DisplayName("featureType: 단일 테이블은 CRUD")
    void testCrudFeatureType() {
        var pageSpec = new PageSpec(
            "page1", "LIST", List.of(), List.of()
        );
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "고객 목록",
            "CRUD", null, "db1", "customers",
            List.of(), List.of(pageSpec), List.of(),
            LocalDateTime.now()
        );

        var decision = classifier.determineFeatureType(spec);
        assertEquals("CRUD", decision.featureType());
        assertTrue(decision.confidence() >= 0.8);
    }

    @Test
    @DisplayName("featureType: primaryTable 없으면 BOARD")
    void testBoardFeatureTypeWithoutPrimaryTable() {
        var pageSpec = new PageSpec(
            "page1", "LIST", List.of(), List.of()
        );
        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "공지사항",
            "BOARD", null, "db1", null,
            List.of(), List.of(pageSpec), List.of(),
            LocalDateTime.now()
        );

        var decision = classifier.determineFeatureType(spec);
        assertEquals("BOARD", decision.featureType());
        assertTrue(decision.confidence() >= 0.5);
    }

    @Test
    @DisplayName("다중 페이지: DASHBOARD")
    void testDashboardArchetype() {
        var page1 = new PageSpec("page1", "LIST", List.of(), List.of());
        var page2 = new PageSpec("page2", "FORM", List.of(), List.of());

        var spec = new ScreenSpecification(
            "spec1", 1, ScreenSpecStatus.DRAFT, "대시보드",
            "DASHBOARD", null, "db1", "customers",
            List.of(), List.of(page1, page2), List.of(),
            LocalDateTime.now()
        );

        var decision = classifier.determineArchetype(spec);
        assertEquals("DASHBOARD", decision.archetype());
        assertTrue(decision.confidence() >= 0.7);
    }
}
