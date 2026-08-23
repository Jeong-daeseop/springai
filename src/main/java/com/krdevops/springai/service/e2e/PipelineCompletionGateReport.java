package com.krdevops.springai.service.e2e;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;
import java.util.Collections;
@Service public class PipelineCompletionGateReport { public Report evaluate(Map<String,Boolean> gates){if(gates==null||gates.isEmpty())throw new IllegalArgumentException("Gate 목록은 필수입니다."); Map<String,Boolean> normalized=new java.util.LinkedHashMap<>(); gates.forEach((key,value)->{if(key==null||key.isBlank())throw new IllegalArgumentException("Gate 이름은 필수입니다."); normalized.put(key,Boolean.TRUE.equals(value));}); return new Report(normalized.values().stream().allMatch(Boolean.TRUE::equals),normalized);} public record Report(boolean passed,Map<String,Boolean> gates){ public Report{gates=Collections.unmodifiableMap(new java.util.LinkedHashMap<>(gates));} public List<String> failedGateNames(){return gates.entrySet().stream().filter(e->!Boolean.TRUE.equals(e.getValue())).map(Map.Entry::getKey).toList();} } }
