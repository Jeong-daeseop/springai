package com.krdevops.springai.model.job;
import com.krdevops.springai.model.artifact.ContentHashes;
import java.time.Instant;

public record GenerationJob(String jobId, String inputHash, State state, int attempts, int progress, Instant expiresAt) {
    public GenerationJob { if(jobId==null||jobId.isBlank()||!ContentHashes.isValid(inputHash)||state==null||attempts<0||progress<0||progress>100||expiresAt==null) throw new IllegalArgumentException("GenerationJob 값이 올바르지 않습니다."); }
    public GenerationJob start(){ return next(State.RUNNING, progress); }
    public GenerationJob progress(int value){ return next(state, value); }
    public GenerationJob complete(){ return next(State.COMPLETED,100); }
    public GenerationJob cancel(){ if(state==State.COMPLETED) throw new IllegalStateException("완료 Job은 취소할 수 없습니다."); return next(State.CANCELLED,progress); }
    public GenerationJob retry(){ if(state!=State.FAILED&&state!=State.CANCELLED) throw new IllegalStateException("실패·취소 Job만 재시도할 수 있습니다."); return new GenerationJob(jobId,inputHash,State.QUEUED,attempts+1,0,expiresAt); }
    private GenerationJob next(State s,int p){return new GenerationJob(jobId,inputHash,s,attempts,p,expiresAt);}
    public enum State { QUEUED,RUNNING,COMPLETED,FAILED,CANCELLED,EXPIRED }
}
