package com.krdevops.springai.controller;

import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.service.thymeleaf.ThymeleafBindingGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** X-API-Key로 보호되는 WP6 JSP→Binding→Thymeleaf Preview 생성 진입점. */
@RestController
@RequestMapping("/api/thymeleaf/binding-generations")
@RequiredArgsConstructor
public class ThymeleafBindingGenerationController {

    private final ThymeleafBindingGenerationService generationService;

    @PostMapping("/preview")
    public ThymeleafBindingGenerationService.BindingPreviewResult preview(
            @RequestBody ThymeleafBindingPreviewRequest request) {
        return generationService.preview(request);
    }
}
