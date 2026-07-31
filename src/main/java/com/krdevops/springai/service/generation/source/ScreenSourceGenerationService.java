package com.krdevops.springai.service.generation.source;

import com.krdevops.springai.service.generation.api.GenerateScreenSourceUseCase;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** featureType에 맞는 {@link ScreenSourceGenerator}를 찾아 위임하는 Strategy 디스패처. */
@Service
@RequiredArgsConstructor
public class ScreenSourceGenerationService implements GenerateScreenSourceUseCase {

    private final List<ScreenSourceGenerator> generators;

    @Override
    public GeneratedSource generate(GenerateScreenSourceCommand command) {
        return generators.stream()
                .filter(generator -> generator.supports(command.featureType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "지원하지 않는 featureType: " + command.featureType()))
                .generate(command);
    }
}
