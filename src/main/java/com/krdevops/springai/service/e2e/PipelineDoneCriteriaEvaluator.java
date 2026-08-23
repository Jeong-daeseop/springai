package com.krdevops.springai.service.e2e;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.List;
import java.util.Collections;
@Service public class PipelineDoneCriteriaEvaluator { public Result evaluate(Map<String,Boolean> criteria){if(criteria==null||criteria.isEmpty())throw new IllegalArgumentException("완료 기준은 필수입니다."); Map<String,Boolean> normalized=new java.util.LinkedHashMap<>(); criteria.forEach((key,value)->{if(key==null||key.isBlank())throw new IllegalArgumentException("완료 기준 이름은 필수입니다."); normalized.put(key,Boolean.TRUE.equals(value));}); return new Result(normalized.values().stream().allMatch(Boolean.TRUE::equals),normalized);} public record Result(boolean complete,Map<String,Boolean> criteria){ public Result{criteria=Collections.unmodifiableMap(new java.util.LinkedHashMap<>(criteria));} public List<String> failedCriteria(){return criteria.entrySet().stream().filter(e->!Boolean.TRUE.equals(e.getValue())).map(Map.Entry::getKey).toList();} } }
