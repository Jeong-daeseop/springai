package com.krdevops.springai.service.migration;
import org.springframework.stereotype.Service;
@Service public class LegacyCompatibilityService { public DualReadResult dualRead(boolean v1Available,boolean v2Available){return new DualReadResult(v1Available,v2Available,v1Available||v2Available);} public void requireLegacyApplyDuringDualRead(boolean dualRead,boolean applyRequested){if(dualRead&&applyRequested)throw new IllegalStateException("이중 읽기 단계에서는 Legacy Apply 경로만 허용됩니다.");} public record DualReadResult(boolean v1Available,boolean v2Available,boolean usable){} }
