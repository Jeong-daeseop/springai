package com.krdevops.springai.service.initializr;

import com.krdevops.springai.model.FilePlan;
import com.krdevops.springai.model.GenerationReport;
import com.krdevops.springai.model.ProjectSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FilePlanExecutor {

    private final EgovFileWriter writer;

    /** 실행: 디스크에 쓰기 */
    public GenerationReport execute(ProjectSpec s, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(s.root().toString());
        for (FilePlan p : plans) {
            try {
                writer.write(s.root(), p.relativePath(), p.content().get());
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());
            }
        }
        return report;
    }

    /** Security 템플릿 저장용 — ProjectSpec 없이 root 경로만으로 실행 */
    public GenerationReport execute(Path root, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(root.toString());
        for (FilePlan p : plans) {
            try {
                writer.write(root, p.relativePath(), p.content().get());
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());
            }
        }
        return report;
    }

    /** 프리뷰: root 경로 버전 */
    public GenerationReport preview(Path root, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(root.toString());
        for (FilePlan p : plans) {
            try {
                p.content().get();
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());
            }
        }
        return report;
    }

    /**
     * 프리뷰: 디스크 쓰기 없이 Supplier만 호출하여 렌더링 검증.
     * 용도: 테스트 / 향후 dryRun=true 파라미터 지원 시 활용.
     * 현재는 내부 테스트 전용.
     */
    public GenerationReport preview(ProjectSpec s, List<FilePlan> plans) {
        GenerationReport report = new GenerationReport(s.root().toString());
        for (FilePlan p : plans) {
            try {
                p.content().get();  // 렌더링만, 쓰기 안 함
                report.added(p);
            } catch (Exception e) {
                report.failed(p, e.getMessage());
            }
        }
        return report;
    }
}
