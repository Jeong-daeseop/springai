package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Region 단위 canonical 구조/내용 Hash를 생성한다. */
@Service
public class OwnershipRegionHashService {

    public GenerationOwnershipManifest.Region hash(String regionId, String canonicalContent,
                                                   GenerationOwnershipManifest.RegionType regionType) {
        if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId는 필수입니다.");
        if (canonicalContent == null) throw new IllegalArgumentException("canonicalContent는 null일 수 없습니다.");
        return new GenerationOwnershipManifest.Region(regionId, regionType,
                ContentHashes.sha256Hex(canonicalContent.getBytes(StandardCharsets.UTF_8)));
    }

    public List<GenerationOwnershipManifest.Region> hashAll(
            Map<String, String> canonicalContentByRegion,
            Map<String, GenerationOwnershipManifest.RegionType> typeByRegion) {
        if (canonicalContentByRegion == null) return List.of();
        return canonicalContentByRegion.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> hash(entry.getKey(), entry.getValue(),
                        typeByRegion == null ? GenerationOwnershipManifest.RegionType.UNKNOWN
                                : typeByRegion.getOrDefault(entry.getKey(), GenerationOwnershipManifest.RegionType.UNKNOWN)))
                .toList();
    }
}
