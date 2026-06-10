package com.krdevops.springai.service.initializr;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EgovFileWriter {

    /**
     * 경로 정규화 + 탈출 방지 추가.
     * validatePlans()의 ".." 체크만으로는 절대경로("/etc/passwd")나
     * symlink 우회를 막을 수 없으므로, normalize() + startsWith()로 이중 방어.
     */
    public void write(Path root, String relativePath, String content) throws IOException {
        Path base   = root.toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new IOException("허용 범위 밖 경로: " + relativePath);
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
