package com.krdevops.springai.service.job;
import com.krdevops.springai.model.job.GenerationJob;
import org.springframework.stereotype.Repository;
import java.util.Map; import java.util.Optional; import java.util.concurrent.ConcurrentHashMap;
@Repository public class GenerationJobRepository { private final Map<String,GenerationJob> jobs=new ConcurrentHashMap<>(); public GenerationJob save(GenerationJob j){if(j==null)throw new IllegalArgumentException("job은 필수입니다."); jobs.put(j.jobId(),j);return j;} public Optional<GenerationJob> find(String id){return Optional.ofNullable(jobs.get(id));} }
