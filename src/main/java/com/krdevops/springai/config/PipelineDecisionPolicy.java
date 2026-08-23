package com.krdevops.springai.config;
import org.springframework.stereotype.Component;
@Component public class PipelineDecisionPolicy { public String uiDesignSpecSchemaVersion(){return "2.0";} public String ownershipRegionStrategy(){return "STRUCTURED_AST_WITH_MARKER_FALLBACK";} public String reviewVisibility(){return "PRIVATE";} public String tokenExportTarget(){return "CSS_CUSTOM_PROPERTIES";} public String jobPolicy(){return "PERSISTED_EXPIRY_RETRY";} }
