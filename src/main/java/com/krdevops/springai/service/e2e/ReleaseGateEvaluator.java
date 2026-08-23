package com.krdevops.springai.service.e2e;
import org.springframework.stereotype.Service;
import java.util.List;
@Service public class ReleaseGateEvaluator {
 public Result evaluate(List<Boolean> gates){if(gates==null||gates.isEmpty())throw new IllegalArgumentException("Release Gate 목록은 필수입니다."); List<Integer> failed=new java.util.ArrayList<>(); for(int i=0;i<gates.size();i++)if(!Boolean.TRUE.equals(gates.get(i)))failed.add(i); return new Result(failed.isEmpty(),failed,List.of());}
 public NamedResult evaluateNamed(java.util.Map<String,Boolean> gates){
  if(gates==null||gates.isEmpty())throw new IllegalArgumentException("Release Gate 목록은 필수입니다.");
  List<String> failed=gates.entrySet().stream().filter(e->e.getKey()==null||e.getKey().isBlank()||!Boolean.TRUE.equals(e.getValue())).map(java.util.Map.Entry::getKey).toList();
  return new NamedResult(failed.isEmpty(),failed);
 }
 public record Result(boolean releasable,List<Integer> failedGateIndexes,List<String> failedGateNames){public Result{failedGateIndexes=List.copyOf(failedGateIndexes==null?List.of():failedGateIndexes);failedGateNames=List.copyOf(failedGateNames==null?List.of():failedGateNames);}}
 public record NamedResult(boolean releasable,List<String> failedGateNames){public NamedResult{failedGateNames=List.copyOf(failedGateNames==null?List.of():failedGateNames);}}
}
