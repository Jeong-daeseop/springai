package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReferencePathValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "pdf");
    private final DesignVisionProperties properties;

    public Path validate(String referencePath) {
        if (referencePath == null || referencePath.isBlank()) {
            throw new IllegalArgumentException("디자인 참조 파일 경로가 필요합니다.");
        }
        try {
            Path path = Path.of(referencePath).toRealPath();
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("일반 파일이 아닙니다: " + path);
            }
            ensureAllowedRoot(path);
            String extension = extension(path);
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new IllegalArgumentException("지원하지 않는 파일 형식입니다: " + extension);
            }
            long maxBytes = properties.getMaxFileMb() * 1024L * 1024L;
            if (Files.size(path) > maxBytes) {
                throw new IllegalArgumentException("파일 크기 제한을 초과했습니다: " + properties.getMaxFileMb() + "MB");
            }
            verifyMagic(path, extension);
            return path;
        } catch (IOException e) {
            throw new IllegalArgumentException("디자인 참조 파일을 확인할 수 없습니다: " + referencePath, e);
        }
    }

    private void ensureAllowedRoot(Path path) throws IOException {
        List<String> configured = properties.getAllowedPaths();
        if (configured == null || configured.isEmpty()) {
            throw new IllegalStateException("app.design-vision.allowed-paths 설정이 필요합니다.");
        }
        boolean allowed = configured.stream()
                .map(this::expandHome)
                .map(Path::of)
                .filter(Files::exists)
                .map(root -> {
                    try { return root.toRealPath(); }
                    catch (IOException e) { return root.toAbsolutePath().normalize(); }
                })
                .anyMatch(path::startsWith);
        if (!allowed) {
            throw new SecurityException("허용된 디자인 참조 경로 밖의 파일입니다: " + path);
        }
    }

    private void verifyMagic(Path path, String extension) throws IOException {
        byte[] header = Files.readAllBytes(path);
        boolean valid = switch (extension) {
            case "pdf" -> startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'});
            case "png" -> startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
            case "jpg", "jpeg" -> header.length >= 2
                    && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("확장자와 파일 내용이 일치하지 않습니다: " + path);
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (source[i] != prefix[i]) return false;
        return true;
    }

    private String extension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String expandHome(String value) {
        if (value == null) return "";
        if (value.equals("~")) return System.getProperty("user.home");
        if (value.startsWith("~/")) return System.getProperty("user.home") + value.substring(1);
        return value;
    }
}
