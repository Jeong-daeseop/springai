package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.generation.model.FeatureType;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;
import com.krdevops.springai.service.generation.model.ScreenType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** featureType에 맞는 {@link ScreenSourceGenerator}로 위임하는 Strategy 디스패처 단위 테스트. */
class ScreenSourceGenerationServiceTest {

    private static final class StubGenerator implements ScreenSourceGenerator {
        private final FeatureType supported;
        private final GeneratedSource result;

        StubGenerator(FeatureType supported, GeneratedSource result) {
            this.supported = supported;
            this.result = result;
        }

        @Override
        public boolean supports(FeatureType featureType) {
            return featureType == supported;
        }

        @Override
        public GeneratedSource generate(GenerateScreenSourceCommand command) {
            return result;
        }
    }

    private GenerateScreenSourceCommand commandFor(FeatureType featureType) {
        return new GenerateScreenSourceCommand(
                featureType, ScreenType.LIST, "com", "TABLE", null, "Domain",
                "egovframework.let.x", Path.of("/tmp/out"), "5.0", "jsp", null, null, null);
    }

    @Test
    void generate_dispatchesToMatchingGenerator() {
        GeneratedSource crudResult = new GeneratedSource(
                FeatureType.CRUD, "Domain", ScreenType.LIST, CrudViewType.JSP, "jspList", Path.of("/x"), "code");
        GeneratedSource boardResult = new GeneratedSource(
                FeatureType.BOARD, "Domain", ScreenType.LIST, CrudViewType.JSP, "jspList", Path.of("/y"), "code");
        ScreenSourceGenerationService service = new ScreenSourceGenerationService(List.of(
                new StubGenerator(FeatureType.CRUD, crudResult),
                new StubGenerator(FeatureType.BOARD, boardResult)));

        assertThat(service.generate(commandFor(FeatureType.CRUD))).isSameAs(crudResult);
        assertThat(service.generate(commandFor(FeatureType.BOARD))).isSameAs(boardResult);
    }

    @Test
    void generate_noSupportingGenerator_throwsIllegalStateException() {
        ScreenSourceGenerationService service = new ScreenSourceGenerationService(List.of(
                new StubGenerator(FeatureType.CRUD, null)));

        assertThatThrownBy(() -> service.generate(commandFor(FeatureType.MASTER_DETAIL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_DETAIL");
    }
}
