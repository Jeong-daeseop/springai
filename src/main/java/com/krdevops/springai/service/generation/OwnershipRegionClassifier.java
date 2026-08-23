package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Region ID 규칙으로 소유권 영역을 보수적으로 분류한다. */
@Service
public class OwnershipRegionClassifier {

    public GenerationOwnershipManifest.RegionType classify(String regionId) {
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId는 필수입니다.");
        String normalized = regionId.trim().toLowerCase(Locale.ROOT);
        if (hasPrefix(normalized, "generated", "gen", "auto")) {
            return GenerationOwnershipManifest.RegionType.GENERATED;
        }
        if (hasPrefix(normalized, "binding", "bind", "contract")) {
            return GenerationOwnershipManifest.RegionType.BINDING;
        }
        if (hasPrefix(normalized, "protected", "protect", "readonly")) {
            return GenerationOwnershipManifest.RegionType.PROTECTED;
        }
        return GenerationOwnershipManifest.RegionType.UNKNOWN;
    }

    public Classification classify(String regionId, String contentHash) {
        return new Classification(regionId, classify(regionId), contentHash);
    }

    private static boolean hasPrefix(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.equals(prefix) || value.startsWith(prefix + ".")
                    || value.startsWith(prefix + ":") || value.startsWith(prefix + "/")
                    || value.startsWith(prefix + "-")) return true;
        }
        return false;
    }

    public record Classification(String regionId,
                                  GenerationOwnershipManifest.RegionType regionType,
                                  String contentHash) {
        public Classification {
            if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId는 필수입니다.");
            if (regionType == null) throw new IllegalArgumentException("regionType은 필수입니다.");
            com.krdevops.springai.model.artifact.ContentHashes.requireValid(contentHash);
        }
    }
}
