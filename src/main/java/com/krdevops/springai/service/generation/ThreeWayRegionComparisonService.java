package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 세 입력의 Region 집합을 합쳐 결정적인 3-way 비교 결과를 만든다. */
@Service
public class ThreeWayRegionComparisonService {
    public List<ThreeWayRegionComparison> compare(Map<String, String> base,
                                                   Map<String, String> current,
                                                   Map<String, String> newer) {
        TreeSet<String> regionIds = new TreeSet<>();
        if (base != null) regionIds.addAll(base.keySet());
        if (current != null) regionIds.addAll(current.keySet());
        if (newer != null) regionIds.addAll(newer.keySet());
        return regionIds.stream().map(id -> ThreeWayRegionComparison.compare(id,
                base == null ? null : base.get(id), current == null ? null : current.get(id),
                newer == null ? null : newer.get(id))).toList();
    }
}
