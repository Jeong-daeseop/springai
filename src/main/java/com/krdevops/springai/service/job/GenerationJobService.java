package com.krdevops.springai.service.job;
import com.krdevops.springai.model.job.GenerationJob;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service public class GenerationJobService { private final GenerationJobRepository repo; public GenerationJobService(GenerationJobRepository repo){this.repo=repo;} public GenerationJob create(String id,String hash,Instant expires){return repo.save(new GenerationJob(id,hash,GenerationJob.State.QUEUED,0,0,expires));} public GenerationJob transition(String id, java.util.function.UnaryOperator<GenerationJob> op){GenerationJob j=repo.find(id).orElseThrow(); return repo.save(op.apply(j));} public GenerationJob get(String id){return repo.find(id).orElseThrow();} }
