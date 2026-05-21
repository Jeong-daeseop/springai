package com.krdevops.springai.chat.controller;

import com.krdevops.springai.chat.service.EgovOllamaModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ollama")
@RequiredArgsConstructor
public class EgovOllamaModelController {

    private final EgovOllamaModelService egovOllamaModelService;

    @Value("${spring.ai.ollama.chat.options.model:mistral}")
    private String defaultModel;

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getInstalledModels() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean available = egovOllamaModelService.isOllamaAvailable();
            response.put("available", available);
            if (available) {
                List<String> models = egovOllamaModelService.getInstalledModels();
                response.put("models", models);
                response.put("count", models.size());
                response.put("defaultModel", defaultModel);
                response.put("success", true);
            } else {
                response.put("models", List.of());
                response.put("count", 0);
                response.put("defaultModel", defaultModel);
                response.put("success", false);
                response.put("message", "Ollama가 설치되지 않았거나 사용할 수 없습니다.");
            }
        } catch (Exception e) {
            log.error("Ollama 모델 목록 조회 오류", e);
            response.put("available", false);
            response.put("models", List.of());
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
}
