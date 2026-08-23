package com.krdevops.springai.config;
import org.springframework.stereotype.Component;
@Component public class PipelineFeatureFlags { private boolean observation; private boolean preview; private boolean apply; public boolean observation(){return observation;} public boolean preview(){return preview;} public boolean apply(){return apply;} public void requireLegacyCompatibility(){if(!observation&&!preview&&!apply)return;} public void setObservation(boolean v){observation=v;} public void setPreview(boolean v){preview=v;} public void setApply(boolean v){apply=v;} }
