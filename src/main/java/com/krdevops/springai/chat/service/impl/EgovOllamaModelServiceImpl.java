package com.krdevops.springai.chat.service.impl;

import com.krdevops.springai.chat.service.EgovOllamaModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EgovOllamaModelServiceImpl implements EgovOllamaModelService {

    @Override
    public List<String> getInstalledModels() {
        List<String> models = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "list");
            pb.redirectErrorStream(true);
            setupPath(pb);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first) { first = false; continue; }
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 1 && !parts[0].equals("NAME")) {
                        models.add(parts[0].trim());
                    }
                }
            }
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            log.error("ollama list 실행 오류", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        return models;
    }

    @Override
    public boolean isOllamaAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "--version");
            pb.redirectErrorStream(true);
            setupPath(pb);
            return pb.start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private void setupPath(ProcessBuilder pb) {
        String os = System.getProperty("os.name").toLowerCase();
        String current = pb.environment().getOrDefault("PATH", "");
        String[] extra = os.contains("mac")
            ? new String[]{"/usr/local/bin", "/opt/homebrew/bin"}
            : new String[]{"/usr/bin", "/usr/local/bin"};
        StringBuilder path = new StringBuilder(current);
        for (String p : extra) {
            if (!current.contains(p)) path.append(":").append(p);
        }
        pb.environment().put("PATH", path.toString());
    }
}
