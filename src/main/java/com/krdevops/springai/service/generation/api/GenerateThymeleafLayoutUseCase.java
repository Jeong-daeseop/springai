package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.layout.GenerateThymeleafLayoutCommand;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult;

public interface GenerateThymeleafLayoutUseCase {

    LayoutGenerationResult generate(GenerateThymeleafLayoutCommand command);
}
