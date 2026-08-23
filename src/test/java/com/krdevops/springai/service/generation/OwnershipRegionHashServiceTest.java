package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class OwnershipRegionHashServiceTest {
    @Test
    void 같은_정규화_내용은_동일한_region_hash를_만든다() {
        var service = new OwnershipRegionHashService();
        var first = service.hash("generated.controller", "class Controller {}",
                GenerationOwnershipManifest.RegionType.GENERATED);
        var second = service.hash("generated.controller", "class Controller {}",
                GenerationOwnershipManifest.RegionType.GENERATED);
        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.contentHash()).hasSize(64);
    }

    @Test
    void 여러_region은_ID순으로_결정적으로_정렬한다() {
        Map<String, String> content = new LinkedHashMap<>();
        content.put("z", "z");
        content.put("a", "a");
        assertThat(new OwnershipRegionHashService().hashAll(content, Map.of()))
                .extracting(GenerationOwnershipManifest.Region::regionId).containsExactly("a", "z");
    }
}
