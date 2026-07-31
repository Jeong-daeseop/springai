package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.service.generation.model.FeatureType;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;

/** featureType별 단일 화면 Source 생성 Strategy. 명세서 §13.2. */
public interface ScreenSourceGenerator {

    boolean supports(FeatureType featureType);

    GeneratedSource generate(GenerateScreenSourceCommand command);
}
