package com.krdevops.springai.service.pipeline;

import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.service.UiDesignSpecArtifactReader;
import com.krdevops.springai.service.UiDesignSpecV2DiffService;
import org.springframework.stereotype.Service;

@Service
public class PipelineQueryFacade {
    private final UiDesignSpecArtifactReader reader; private final UiDesignSpecV2DiffService diff;
    public PipelineQueryFacade(UiDesignSpecArtifactReader reader, UiDesignSpecV2DiffService diff){this.reader=reader;this.diff=diff;}
    public UiDesignSpecV2 getUiDesignSpecV2(String artifactId){return reader.read(artifactId).spec();}
    public UiDesignSpecV2DiffService.SpecDiff compareUiDesignSpecVersions(UiDesignSpecV2 base, UiDesignSpecV2 target){return diff.compare(base,target);}
}
