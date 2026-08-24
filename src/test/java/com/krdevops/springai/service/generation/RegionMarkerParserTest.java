package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionMarkerParserTest {

    @Test
    void 내용이_null이거나_비어있으면_빈_리스트를_반환한다() {
        assertThat(RegionMarkerParser.parse(null)).isEmpty();
        assertThat(RegionMarkerParser.parse("")).isEmpty();
    }

    @Test
    void 마커가_없으면_파일_전체를_generated_단일_Region으로_취급한다() {
        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse("class Foo {}");

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).regionId()).isEqualTo("generated.file");
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.GENERATED);
        assertThat(regions.get(0).content()).isEqualTo("class Foo {}");
    }

    @Test
    void Java_주석_마커로_감싼_구간을_Region으로_분리한다() {
        String content = """
                class ServiceImpl {
                    public void run() {
                        // @region:generated:body start
                        doStandardCrud();
                        // @region:generated:body end
                        // @region:protected:custom start
                        doCustomLogic();
                        // @region:protected:custom end
                    }
                }
                """;

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(2);
        assertThat(regions.get(0).regionId()).isEqualTo("body");
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.GENERATED);
        assertThat(regions.get(0).content()).contains("doStandardCrud();");
        assertThat(regions.get(1).regionId()).isEqualTo("custom");
        assertThat(regions.get(1).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.PROTECTED);
        assertThat(regions.get(1).content()).contains("doCustomLogic();");
    }

    @Test
    void HTML과_JSP_주석_문법도_동일하게_인식한다() {
        String html = "<!-- @region:binding:table start -->x<!-- @region:binding:table end -->";
        String jsp = "<%-- @region:binding:table start --%>x<%-- @region:binding:table end --%>";

        assertThat(RegionMarkerParser.parse(html)).hasSize(1);
        assertThat(RegionMarkerParser.parse(jsp)).hasSize(1);
        assertThat(RegionMarkerParser.parse(html).get(0).regionType())
                .isEqualTo(GenerationOwnershipManifest.RegionType.BINDING);
    }

    @Test
    void 마커_사이의_비마커_구간은_어떤_Region으로도_파싱되지_않는다() {
        String content = "IMPORTS\n// @region:generated:a start\nA\n// @region:generated:a end\nGAP";

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).content()).isEqualTo("\nA\n");
    }

    @Test
    void end_마커가_없으면_파일_전체를_UNKNOWN으로_강등한다() {
        String content = "// @region:protected:custom start\nA";

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).regionId()).isEqualTo("unknown.file");
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.UNKNOWN);
    }

    @Test
    void 같은_id가_중복되면_파일_전체를_UNKNOWN으로_강등한다() {
        String content = """
                // @region:generated:dup start
                A
                // @region:generated:dup end
                // @region:generated:dup start
                B
                // @region:generated:dup end
                """;

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).regionType()).isEqualTo(GenerationOwnershipManifest.RegionType.UNKNOWN);
    }

    @Test
    void 해시는_같은_내용에_대해_결정적이다() {
        assertThat(RegionMarkerParser.hashOf("same")).isEqualTo(RegionMarkerParser.hashOf("same"));
        assertThat(RegionMarkerParser.hashOf("a")).isNotEqualTo(RegionMarkerParser.hashOf("b"));
    }

    @Test
    void end_마커가_같은_줄의_코드_뒤에_오면_내용이_stripped되지_않는다() {
        String content = "// @region:generated:x start\ndoWork(); // @region:generated:x end\nAFTER";

        List<RegionMarkerParser.ParsedRegion> regions = RegionMarkerParser.parse(content);

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).content()).isEqualTo("\ndoWork(); // ");
    }

    @Test
    void hashOf_null이_들어오면_IllegalArgumentException을_던진다() {
        assertThatThrownBy(() -> RegionMarkerParser.hashOf(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("regionContent");
    }
}
