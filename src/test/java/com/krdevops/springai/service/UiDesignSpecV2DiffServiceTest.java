package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiDesignSpecV2DiffServiceTest {

    private final UiDesignSpecV2DiffService service = new UiDesignSpecV2DiffService();

    @Test
    void Viewport_차이를_Reflow_Hide_Swap_Alternate로_구분한다() {
        UiDesignSpecV2 spec = spec("a", "r1", List.of("a", "b", "c", "x", "y", "z"), List.of(
                structure("desktop", "a", "b", "c"),
                structure("tablet-reflow", "b", "a", "c"),
                structure("tablet-hide", "a", "b"),
                structure("mobile-swap", "a", "b", "x"),
                structure("mobile-alt", "x", "y", "z")));

        List<UiDesignSpecV2DiffService.ResponsiveChange> changes = service.analyzeResponsive(spec);

        assertThat(changes).extracting(UiDesignSpecV2DiffService.ResponsiveChange::type)
                .contains(
                        UiDesignSpecV2DiffService.ResponsiveChangeType.REFLOW,
                        UiDesignSpecV2DiffService.ResponsiveChangeType.HIDE,
                        UiDesignSpecV2DiffService.ResponsiveChangeType.SWAP,
                        UiDesignSpecV2DiffService.ResponsiveChangeType.ALTERNATE_STRUCTURE);
    }

    @Test
    void Version_Diff가_Node와_Viewport와_Source_Revision_변경을_보고한다() {
        UiDesignSpecV2 base = spec("a", "r1", List.of("a", "b"),
                List.of(structure("desktop", "a", "b")));
        UiDesignSpecV2 target = specWithGeometry(
                "b", "r2", List.of("a", "b", "c"),
                List.of(structure("desktop", "b", "a", "c")), "a", 200);

        UiDesignSpecV2DiffService.SpecDiff diff = service.compare(base, target);

        assertThat(diff.contentChanged()).isTrue();
        assertThat(diff.sourceRevisionChanged()).isTrue();
        assertThat(diff.nodeChanges()).anySatisfy(change -> {
            assertThat(change.semanticId()).isEqualTo("a");
            assertThat(change.type()).isEqualTo(UiDesignSpecV2DiffService.ChangeType.MODIFIED);
            assertThat(change.changedFields()).contains("geometry");
        }).anySatisfy(change -> {
            assertThat(change.semanticId()).isEqualTo("c");
            assertThat(change.type()).isEqualTo(UiDesignSpecV2DiffService.ChangeType.ADDED);
        });
        assertThat(diff.viewportChanges()).containsExactly(
                new UiDesignSpecV2DiffService.ViewportVersionChange(
                        "desktop", UiDesignSpecV2DiffService.ChangeType.MODIFIED));
    }

    @Test
    void 동일_Spec은_빈_Diff를_생성한다() {
        UiDesignSpecV2 spec = spec("a", "r1", List.of("a"),
                List.of(structure("desktop", "a")));

        UiDesignSpecV2DiffService.SpecDiff diff = service.compare(spec, spec);

        assertThat(diff.contentChanged()).isFalse();
        assertThat(diff.sourceRevisionChanged()).isFalse();
        assertThat(diff.nodeChanges()).isEmpty();
        assertThat(diff.viewportChanges()).isEmpty();
    }

    private UiDesignSpecV2 spec(
            String hashCharacter,
            String revision,
            List<String> nodeIds,
            List<UiDesignSpecV2.ResponsiveStructure> structures) {
        return specWithGeometry(hashCharacter, revision, nodeIds, structures, null, 100);
    }

    private UiDesignSpecV2 specWithGeometry(
            String hashCharacter,
            String revision,
            List<String> nodeIds,
            List<UiDesignSpecV2.ResponsiveStructure> structures,
            String changedNode,
            double changedWidth) {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:1"), 1, "TEST", false, false);
        List<UiDesignSpecV2.SemanticNode> nodes = new ArrayList<>();
        for (String id : nodeIds) {
            double width = id.equals(changedNode) ? changedWidth : 100;
            nodes.add(new UiDesignSpecV2.SemanticNode(
                    id, "container", null,
                    new UiDesignSpecV2.Geometry(0, 0, width, 40),
                    java.util.Map.of(), null, List.of(), List.of(), evidence));
        }
        return new UiDesignSpecV2(
                "ui-1", "2.0", hashCharacter.repeat(64),
                new UiDesignSpecV2.Source(
                        UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", revision),
                null, nodes, List.of(), structures, List.of(), List.of(), 1);
    }

    private UiDesignSpecV2.ResponsiveStructure structure(String viewport, String... ids) {
        return new UiDesignSpecV2.ResponsiveStructure(
                viewport, List.of(ids), List.of(ids));
    }
}
