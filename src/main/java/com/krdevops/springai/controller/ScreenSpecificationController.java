package com.krdevops.springai.controller;

import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.ScreenSpecValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 승인된 업무 ScreenSpecification을 불변 버전 Snapshot으로 Import한다. */
@RestController
@RequestMapping("/api/screen-specifications")
@RequiredArgsConstructor
public class ScreenSpecificationController {
    private final ScreenSpecRepository repository;
    private final ScreenSpecValidator validator;
    private final ObjectMapper objectMapper;

    @PostMapping("/import")
    public ScreenSpecification importSpecification(@RequestBody String body) {
        final ScreenSpecification candidate;
        try {
            candidate = objectMapper.copy().findAndRegisterModules()
                    .readValue(body, ScreenSpecification.class);
        } catch (Exception exception) {
            throw new FigmaRequestException("SCREEN_SPECIFICATION_JSON_INVALID",
                    "Screen Specification JSON을 읽을 수 없습니다: " + exception.getMessage());
        }
        ScreenSpecification validated = validator.validate(candidate);
        if (validated.status() != ScreenSpecStatus.APPROVED || !validated.issues().isEmpty()) {
            throw new FigmaRequestException("SCREEN_SPECIFICATION_INVALID",
                    "APPROVED 상태이고 issues가 비어 있는 명세만 Import할 수 있습니다: " + validated.issues());
        }
        repository.saveImmutable(validated);
        return validated;
    }

    @GetMapping("/{id}/versions/{version}")
    public ScreenSpecification findVersion(@PathVariable String id, @PathVariable int version) {
        return repository.findVersion(id, version)
                .orElseThrow(() -> new FigmaResourceNotFoundException(
                        "SCREEN_SPECIFICATION_NOT_FOUND", id + "/" + version));
    }
}
