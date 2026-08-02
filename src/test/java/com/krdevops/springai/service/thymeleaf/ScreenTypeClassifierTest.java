package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
