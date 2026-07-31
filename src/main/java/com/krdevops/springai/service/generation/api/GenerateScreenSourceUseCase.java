package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;

public interface GenerateScreenSourceUseCase {

    GeneratedSource generate(GenerateScreenSourceCommand command);
}
