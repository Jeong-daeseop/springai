package com.krdevops.springai.service.generation;

import com.krdevops.springai.service.generation.board.BoardGenerationPipelineService;
import com.krdevops.springai.service.generation.board.BoardProjectGenerationService;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationPipelineService;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailProjectGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영 Use Case의 주입 경계가 구형 Orchestrator로 되돌아가지 않는지 고정한다. */
class GenerationCompatibilityBoundaryTest {

    @Test
    void legacyOrchestratorTypes_areRemoved() {
        assertThat(classIsPresent("com.krdevops.springai.service.BoardOrchestrationService")).isFalse();
        assertThat(classIsPresent("com.krdevops.springai.service.MasterDetailOrchestrationService")).isFalse();
        assertThat(classIsPresent(
                "com.krdevops.springai.service.generation.board.BoardOrchestrationCompatibilityFacade")).isFalse();
        assertThat(classIsPresent(
                "com.krdevops.springai.service.generation.masterdetail.MasterDetailOrchestrationCompatibilityFacade"))
                .isFalse();
    }

    @Test
    void boardAutowiredConstructor_usesPipelineOnly() {
        var constructor = Arrays.stream(BoardProjectGenerationService.class.getConstructors())
                .filter(candidate -> candidate.isAnnotationPresent(Autowired.class)).findFirst().orElseThrow();
        assertThat(constructor.getParameterTypes()).containsExactly(
                BoardGenerationPipelineService.class,
                com.krdevops.springai.service.generation.board.BoardGenerationResultAssembler.class);
    }

    @Test
    void masterDetailAutowiredConstructor_usesPipelineOnly() {
        var constructor = Arrays.stream(MasterDetailProjectGenerationService.class.getConstructors())
                .filter(candidate -> candidate.isAnnotationPresent(Autowired.class)).findFirst().orElseThrow();
        assertThat(constructor.getParameterTypes()).containsExactly(
                MasterDetailGenerationPipelineService.class,
                com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationResultAssembler.class);
    }

    private static boolean classIsPresent(String className) {
        try {
            Class.forName(className, false, GenerationCompatibilityBoundaryTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
